package ru.heckzero.server.world;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.Item;
import ru.heckzero.server.items.ItemsDct;
import ru.heckzero.server.user.User;
import ru.heckzero.server.user.UserManager;

import javax.persistence.*;
import java.util.*;
import java.util.stream.Collectors;

@Entity(name = "Portal")
@Table(name = "portals")
@PrimaryKeyJoinColumn(name = "b_id")
public class Portal extends Building {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final EnumSet<Params> portalParams = EnumSet.of(Params.cash, Params.ds, Params.city, Params.p1, Params.p2);

    private int ds;                                                                                                                         //discount (%) for citizens arriving to this portal
    private String city;                                                                                                                    //of that city
    private String p1;                                                                                                                      //resources needed to teleport 1000 weight units ?
    private String p2;                                                                                                                      //corsair clone price
    private String bigmap_city;                                                                                                             //city on bigmap this portal represents
    private boolean bigmap_enabled;                                                                                                         //should this portal be shown on a bigmap

    @OneToMany(mappedBy = "srcPortal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private final List<PortalRoute> routes = new ArrayList<>();

    public static Portal getPortal(int id) {                                                                                                //try to get Portal by building id from a database
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Portal> query = session.createQuery("select p from Portal p left join fetch p.routes pr left join fetch pr.dstPortal p_dst left join fetch p_dst.location where p.id = :id order by p_dst.txt", Portal.class).setParameter("id", id).setCacheable(true);
            Portal portal = query.getSingleResult();
            if (portal != null)
                portal.ensureInitialized();
            return portal;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load portal with id %d from database: %s", id, e.getMessage());
        }
        return new Portal();                                                                                                                //in case of portal was not found or database error return a default portal instance
    }

    public static List<Portal> getBigmapPortals() {                                                                                         //generate world map data - cities, portal and routes
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Portal> query = session.createQuery("select p from Portal p inner join fetch p.location l left join fetch p.routes pr where p.bigmap_enabled = true", Portal.class).setCacheable(true).setReadOnly(true);
            List<Portal> portals = query.list();
            portals.forEach(Portal::ensureInitialized);                                                                                     //initialize location and routes on subsequent queries from L2 cache
            return portals;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load bigmap data: %s", e.getMessage());
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    private static List<PortalRoute> getComeinRoutes(int p_dst_id) {                                                                         //get routes for the given destination portal id
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<PortalRoute> query = session.createQuery("select pr from PortalRoute pr inner join fetch pr.srcPortal p_src inner join fetch pr.dstPortal p_dst inner join fetch p_src.location where p_dst.id = :id order by p_src.txt", PortalRoute.class).setParameter("id", p_dst_id).setCacheable(true);
            List<PortalRoute> comeinRoutes = query.list();
            comeinRoutes.forEach(r -> Hibernate.initialize(r.getSrcPortal().getLocation()));                                                //initialize included fields on subsequent queries from L2 cache
            return comeinRoutes;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't get incoming routes for destination portal id %d: %s:%s", p_dst_id, e.getClass().getSimpleName(), e.getMessage());
        }
        return Collections.emptyList();
    }

    protected Portal() { }

    private void ensureInitialized() {                                                                                                      //initialize portal fields from L2 cache
        Hibernate.initialize(getLocation());
        routes.forEach(r -> Hibernate.initialize(r.getDstPortal().getLocation()));
        return;
    }
    public String getCity() {return city;}                                                                                                  //these citizens have a discount when they are flying TO this portal
    public int getDs()      {return ds;}                                                                                                    //discount for citizens of the 'city' which are flying to that portal
    public String getP2()   {return p2;}

    public boolean setDs(int ds) {this.ds = ds; return sync();}

    private String getXmlRoutes() {return routes.stream().filter(PortalRoute::isEnabled).map(PortalRoute::getXml).collect(Collectors.joining());} //portal routes XML formatted in <O />

    public String bigMapXml() {                                                                                                             //generate an object for the bigmap (city and/or portal)
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(bigmap_city))                                                                                            //this portal "represents" a city on bigmap
            sb.append(String.format("<city name=\"%s\" xy=\"%s,%s\"/>", bigmap_city, getLocation().getLocalX(), getLocation().getLocalY()));

        String routes = this.routes.stream().filter(PortalRoute::isBigmapEnabled).map(PortalRoute::getBigMap).collect(Collectors.joining(";")); //get portal routes (from this portal)
        if (!routes.isEmpty())
            sb.append(String.format("<portal name=\"%s\" xy=\"%d,%d\" linked=\"%s;\"/>", getParamStr(Building.Params.txt), getLocation().getLocalX(), getLocation().getLocalY(), routes));
        return sb.toString();
    }

    public String cominXml() {                                                                                                              //get incoming routes as XML string
        StringJoiner sj = new StringJoiner("", "<PR comein=\"1\">", "</PR>");
        List<PortalRoute> comeinRoutes = getComeinRoutes(getId());                                                                          //get incoming routes for the current portal
        comeinRoutes.forEach(r -> sj.add(r.getXmlComein()));                                                                                //add each route as XML string
        return sj.toString();
    }

    public String prXml(boolean withWh) {                                                                                                   //XML formatted portal data including routes and warehouse items
        StringJoiner sj = new StringJoiner("", "", "</PR>");
        sj.add(portalParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<PR ", ">"))); //add XML portal params
        sj.add(getXmlRoutes());                                                                                                             //add XML portal routes
        if (withWh)                                                                                                                         //if we should add warehouse content
            sj.add(getItemBox().getXml());                                                                                                  //add portal warehouse items (resources)
        return sj.toString();
    }

    public boolean consumeRes(int userWeight) {                                                                                             //consume portal resources for the teleportation
        String [] res = p1.split(",");                                                                                                      //p1 - resources needed to teleport 1000 weight units
        Map<Item, Integer> itemsToConsume = new HashMap<>();                                                                                //found items to consume with consume

        for (int i = 0; i < res.length; i++) {
            if (res[i].isEmpty())                                                                                                           //res doesn't contain resource of number i
                continue;
            Item item = getItemBox().findItemByType(Double.parseDouble(String.format("0.19%d", i + 1)));                                    //find needed resource on portal warehouse
            int requiredRes = NumberUtils.toInt(res[i]) * userWeight / 1000;                                                                //compute resource amount required for teleportation
            if (item == null || item.getCount() < requiredRes) {
                logger.warn("portal id %d %s has got not enough resources of type %d, (needed %d, has got %d)", getId(), getTxt(), i + 1, requiredRes, item == null ? 0 : item.getCount());
                return false;
            }
            itemsToConsume.put(item, requiredRes);                                                                                          //item to consume and a count to decrease
        }
        itemsToConsume.forEach((k, v) -> logger.debug("consuming resource %s of count %d in portal %d %s", k.getParamStr(Item.Params.txt), v, getId(), getTxt()));
        itemsToConsume.forEach((k, v) -> getItemBox().delItem(k.getId(), v));
        return true;
    }

    public void processCmd(int comein, int id, double new_cost, int to, long d, long a, int s, int c, int get, int ds, User user) {
        if (get != -1) {                                                                                                                    //withdraw money from portal cash
            int cashTaken = this.decMoney(get);
            user.addMoney(ItemsDct.MONEY_COPP, cashTaken);
            return;
        }

        if (ds != -1) {                                                                                                                     //set a portal citizen arrival discount
            if (!setDs(ds)) {                                                                                                               //set a new discount
                user.disconnect();
                return;
            }
            user.sendMsg(String.format("<PR ds=\"%d\" city=\"%s\" p2=\"%s\"/>", getDs(), getCity(), getP2()));                              //update portal information
            return;
        }

        if (comein != 0) {                                                                                                                  //incoming routes request
            user.sendMsg(cominXml());                                                                                                       //get and send incoming routes for the current portal
            return;
        }

        if (id != -1 && new_cost != -1.0) {                                                                                                 //changing an incoming route cost
            PortalRoute route = PortalRoute.getRoute(id);                                                                                   //get the route to change cost for
            if (route == null || !route.setCost(new_cost))
                user.disconnect();
            return;
        }

        if (to != -1) {                                                                                                                     //flight request to = routeId where user wants to fly to
            PortalRoute route = PortalRoute.getRoute(to);
            if (route == null) {                                                                                                            //can't get the route user wants flying to
                user.disconnect();
                return;
            }

            int mass = user.getMass().get("tk");                                                                                            //current user mass
            Item passport = user.getPassport();
            int cost = route.getFlightCost(mass, passport == null ? StringUtils.EMPTY : passport.getParamStr(Item.Params.res));             //get the flight cost
            if (!user.isGod() && cost > user.getMoney().getCopper()) {                                                                      //user is out of money
                user.sendMsg("<PR err=\"1\"/>");
                return;
            }
            if (!consumeRes(mass)) {                                                                                                        //portal is out of resources to commit the flight
                user.sendMsg("<PR err=\"5\"/>");
                return;
            }

            Portal dstPortal = route.getDstPortal();
            int X = dstPortal.getLocation().getX();
            int Y = dstPortal.getLocation().getY();
            int Z = dstPortal.getZ();
            int hz = dstPortal.getName();
            int ROOM = route.getROOM();

            user.decMoney(cost);                                                                                                            //decrease money from user
            if (!user.getBuilding().addMoney(cost)) {                                                                                       //add money to portal's cash
                user.disconnect(UserManager.ErrCodes.SRV_FAIL);
                return;
            }
            user.setRoom(X, Y, Z, hz, ROOM);
            user.sendMsg(String.format("<MYPARAM kupol=\"%d\"/><PR X=\"%d\" Y=\"%d\" Z=\"%d\" hz=\"%d\" ROOM=\"%d\"/>", user.getParamInt(User.Params.kupol), X, Y, Z, hz, ROOM));
            return;
        }

        if (d != -1) {                                                                                                                      //user puts resource-item to portal's warehouse
            Set<Item.Params> resetParams = Set.of(Item.Params.user_id);
            Map<Item.Params, Object> setParams = Map.of(Item.Params.b_id, getId());
            if (!user.getItemBox().joinMoveItem(d, c, false, user::getNewId, this.getItemBox(), resetParams, setParams)) {
                logger.error("can't put item id %d to portal warehouse", d);
                user.disconnect();
            }
            return;
        }

        if (a != -1) {                                                                                                                      //user takes an item from portal warehouse
            Item takenItem = this.getItemBox().getClonnedItem(a, c, user::getNewId);
            if (takenItem == null) {                                                                                                        //item doesn't exist on warehouse already
                user.sendMsg("<PR a1=\"0\" a2=\"0\"/>");                                                                                    //let the client know if it failed in taking an item
                return;
            }

            takenItem.resetParam(Item.Params.b_id, false);                                                                                  //set new params before transfer the item to user's item box
            takenItem.setParam(Item.Params.user_id, user.getId(), false);
            takenItem.setParam(Item.Params.section, s, false);
            user.getItemBox().addItem(takenItem);                                                                                           //add the item to user's item box

            Item item = this.getItemBox().findItem(a);                                                                                      //we have to check the remnant count of the source item
            int a2 = (item == null) ? 0 : item.getCount();                                                                                  //the item remainder count
            user.sendMsg(String.format("<PR a1=\"%d\" a2=\"%d\"/>", takenItem.getCount(), a2));                                             //a1 - how much was taken, a2 - item remainder
            return;
        }

        user.sendMsg(prXml(user.isBuildMaster()));                                                                                          //user entered a portal, sending info about that portal, its routes and warehouse items
        return;
    }

}
