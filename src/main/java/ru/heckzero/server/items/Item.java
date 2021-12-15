package ru.heckzero.server.items;

import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.LongType;
import ru.heckzero.server.ParamUtils;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

@org.hibernate.annotations.NamedQuery(name = "ItemBox_USER", query = "select distinct i from Item i left join fetch i.included where i.user_id = :id order by i.id")
@org.hibernate.annotations.NamedQuery(name = "ItemBox_BUILDING", query = "select i from Item i left join fetch i.included where i.b_id = :id order by i.id")
@org.hibernate.annotations.NamedQuery(name = "ItemBox_BANK_CELL", query = "select i from Item i left join fetch i.included where i.cell_id = :id order by i.cell_id")
@org.hibernate.annotations.NamedQuery(name = "Item_DeleteItemByIdWithoutSub", query = "delete from Item i where i.id = :id")
@org.hibernate.annotations.NamedQuery(name = "Item_DeleteItemByIdWithSub", query = "delete from Item i where i.id = :id or i.pid = :id")
@Entity(name = "Item")
@Table(name = "items_inventory")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "Item_Region")
public class Item implements Cloneable {
    private static final Logger logger = LogManager.getFormatterLogger();

    public enum Params {id, pid,    user_id, section, slot,  b_id, cell_id,  name, txt, massa, st, made, min, protect, quality, maxquality, OD, rOD, type, damage, calibre, shot, nskill, max_count, up, grouping, range, nt, build_in, c, radius, cost, cost2, s1, s2, s3, s4, count, lb, dt, hz, res, owner, tm, ln}
    private static final EnumSet<Params> itemParams = EnumSet.of(Params.id, Params.section, Params.slot, Params.name, Params.txt, Params.massa, Params.st, Params.made, Params.min, Params.protect, Params.quality, Params.maxquality, Params.OD, Params.rOD, Params.type, Params.damage, Params.calibre, Params.shot, Params.nskill, Params.max_count, Params.up, Params.grouping, Params.range, Params.nt, Params.build_in, Params.c, Params.radius, Params.cost, Params.cost2, Params.s1, Params.s2, Params.s3, Params.s4, Params.count, Params.lb, Params.dt, Params.hz, Params.res, Params.owner, Params.tm, Params.ln);

    @SuppressWarnings("unchecked")
    public static long getNextGlobalId() {                                                                                                  //get next main id for the item from the sequence
        try (Session session = ServerMain.sessionFactory.openSession()) {
            NativeQuery<Long> query = session.createSQLQuery("select nextval('main_id_seq') as nextval").addScalar("nextval", LongType.INSTANCE);
            return query.getSingleResult();
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't get next main id for the item: %s:%s", e.getClass().getSimpleName(), e.getMessage());
        }
        return -1;
    }
    @SuppressWarnings("unchecked")
    public static List<Long> getNextGlobalId(int num) {                                                                                     //get some amount main ids from a sequence
        try (Session session = ServerMain.sessionFactory.openSession()) {
            NativeQuery<Long> query = session.createSQLQuery("select nextval('main_id_seq') from generate_series(1, :num) as nextval").setParameter("num", num).addScalar("nextval", LongType.INSTANCE);
            return query.list();
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't get next main id for the item: %s:%s", e.getClass().getSimpleName(), e.getMessage());
        }
        return LongStream.range(0, num).map(i -> -1L).boxed().toList();                                                                     //will return a list filled by -1
    }

    public static boolean delFromDB(long id, boolean withSub) {                                                                             //delete item from database
        Transaction tx = null;
        try (Session session = ServerMain.sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.createNamedQuery(withSub ? "Item_DeleteItemByIdWithSub" : "Item_DeleteItemByIdWithoutSub").setParameter("id", id).executeUpdate();
            tx.commit();
            return true;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't delete item id %d from database: %s:%s", id, e.getClass().getSimpleName(), e.getMessage());
            if (tx != null && tx.isActive())
                tx.rollback();
        }
        return false;
    }

    @Id
    private long id;

    private Long pid;                                                                                                                       //parent item id (-1 means no parent, a master item)
    private String name = "foobar";                                                                                                         //item name in a client (.swf and sprite)
    private String txt = "Unknown";                                                                                                         //item text representation for human
    private String massa;                                                                                                                   //item weight
    private String st;                                                                                                                      //slots- appropriate slots this item can be wear on
    private String made;                                                                                                                    //made by
    private String min;                                                                                                                     //minimal requirements
    private String protect;                                                                                                                 //item protection properties
    private String quality, maxquality;                                                                                                     //current item quality
    @Column(name = "\"OD\"") private String OD;                                                                                             //OD needed to use an item
    @Column(name = "\"rOD\"") private String rOD;                                                                                           //OD needed for reload
    private double type;
    private String damage;
    private String calibre;
    private String shot;
    private String nskill;                                                                                                                  //category (perk?) name-skill?
    private String max_count;                                                                                                               //max number of included items allowed
    private String up;                                                                                                                      //user parameters this item does influence on
    private String grouping;
    private String range;                                                                                                                   //range of action
    private String nt = "1";                                                                                                                //no transfer, this item can't be transferred
    private String build_in;
    private String c;                                                                                                                       //item category
    private String radius;
    private String cost, cost2;                                                                                                             //something related to shop sales
    private String s1, s2, s3, s4;                                                                                                          //s4 - LANG[perk_group_1]
    private String count;                                                                                                                   //item count
    private String lb;
    private String dt;                                                                                                                      //expiry date
    private String hz;
    private String res;
    private String owner;                                                                                                                   //item owner
    private String tm;
    private String ln;                                                                                                                      //long name text

    private Integer user_id;                                                                                                                //user id this item belongs to
    private String section = StringUtils.EMPTY;                                                                                             //the section number in user box or building warehouse
    private String slot = StringUtils.EMPTY;                                                                                                //user's slot this item is on

    private Integer b_id;                                                                                                                   //building id this item belongs to
    private Integer cell_id;                                                                                                                //bank cell id

    @OneToMany(fetch = FetchType.LAZY)                                                                                                      //built-in items having item.pid = item.id
    @JoinTable(name = "items_inventory", joinColumns = {@JoinColumn(name = "pid")}, inverseJoinColumns = {@JoinColumn(name = "id")})
    private List<Item> included = new ArrayList<>();

    protected Item() { }

    public Item(ItemTemplate itmpl) {                                                                                                       //create (clone) an Item from ItemTemplate
        Field[ ] tmplFields = ItemTemplate.class.getDeclaredFields();
        Arrays.stream(tmplFields).filter(f -> !Modifier.isStatic(f.getModifiers())).forEach(f -> {
            try {
                FieldUtils.getField(Item.class, f.getName(), true).set(this, FieldUtils.readField(f, itmpl instanceof HibernateProxy ? Hibernate.unproxy(itmpl) : itmpl, true));
            } catch (IllegalAccessException e) {
                logger.error("can't create an Item from the template: %s", e.getMessage());
            }
        });
        return;
    }

    public boolean isIncluded()   {return pid != null && pid != -1;}                                                                        //item is an included one itself (it has pid = parent.id)
    public boolean isExpired()    {return Range.between(1L, Instant.now().getEpochSecond()).contains(getParamLong(Params.dt));}             //item is expired (has dt < now)
    public boolean isNoTransfer() {return getParamInt(Params.nt) == 1;}                                                                     //check if the item has nt = 1 - no transfer param set
    public boolean isRes()        {return getParamStr(Params.name).split("-")[1].charAt(0) == 's' && getCount() > 0;}                       //item is a resource, may be enriched
    public boolean isDrug()       {return (getBaseType() == 796 || getBaseType() == 797) && getCount() > 0;}                                //item is a drug or inject pistol
    public boolean isBldKey()     {return getBaseType() == ItemsDct.BASE_TYPE_BLD_KEY;}                                                     //item is a building owner key

    public boolean needCreateNewId(int count) {return count > 0 && count < getParamInt(Params.count) || getParamDouble(Params.calibre) > 0.0;} //shall we create a new id when do something with this item

    public Long getId()   {return id; }                                                                                                     //item id
    public long getPid()  {return pid;}                                                                                                     //item master's id
    public int getCount() {return getParamInt(Params.count);}                                                                               //item count - 0 if item is not stackable
    public int getBaseType() {return (int)Math.floor(getParamDouble(Params.type));}                                                         //get the base type of the item

    public ItemBox getIncluded() {return Hibernate.isInitialized(included) ? new ItemBox(included) : new ItemBox();}                        //return included items as item box
    public ItemBox getAllItems() {
        ItemBox box = Hibernate.isInitialized(included) ? new ItemBox(included) : new ItemBox();
        box.addItem(this);
        return box;
    }

    synchronized public boolean setParam(Params paramName, Object paramValue, boolean sync) {                                               //set an item param to paramValue
        if (ParamUtils.setParam(this, paramName.toString(), paramValue)) {                                                                  //delegate param setting to ParamUtils
            return !sync || sync();
        }
        return false;
    }
    public void setParams(Map<Params, Object> params, boolean sync) {params.forEach((p, v) -> setParam(p, v, sync));}
    public boolean resetParam(Params paramName, boolean sync) {return setParam(paramName, null, sync);}
    public void resetParams(Set<Item.Params> resetParams, boolean sync) {resetParams.forEach(p -> resetParam(p, sync));}

    public boolean setId(long id, boolean sync)      {return setParam(Params.id, id, sync);}
    public boolean setGlobalId(boolean sync) {return setId(getNextGlobalId(), sync);}

    public String getParamStr(Params param)    {return ParamUtils.getParamStr(this, param.toString());}
    public int getParamInt(Params param)       {return ParamUtils.getParamInt(this, param.toString());}
    public long getParamLong(Params param)     {return ParamUtils.getParamLong(this, param.toString());}
    public double getParamDouble(Params param) {return ParamUtils.getParamDouble(this, param.toString());}
    private String getParamXml(Params param)   {return ParamUtils.getParamXml(this, param.toString());}                                     //get param as XML attribute, will return an empty string if value is empty and appendEmpty == false

    public String getXml() {return getXml(true);}
    public String getXml(boolean withIncluded) {
        StringJoiner sj = new StringJoiner("", "", "</O>");
        sj.add(itemParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<O ", ">")));    //parent item
        sj.add(withIncluded && Hibernate.isInitialized(included) ? included.stream().map(i -> itemParams.stream().map(i::getParamXml).filter(StringUtils::isNoneBlank).collect(Collectors.joining(" ", "<O ", "/>"))).collect(Collectors.joining()) : StringUtils.EMPTY);
        return sj.toString();
    }

    public boolean removeSub(long id) {return included.removeIf(i -> i.getId() == id);}                                                     //remove and item from included

    public boolean decrement(boolean sync) {return decrease(1, sync);}
    public boolean increase(int num, boolean sync) {return setParam(Params.count, getCount() + num, sync);}                                 //increase item count by num
    public boolean decrease(int num, boolean sync) {                                                                                        //decrease item count by num
        int count = getCount();
        if (num > 0 && num < count)                                                                                                         //check if the item count > num
            return setParam(Params.count, count - num, sync);
        else
            logger.error("can't decrease item id %d by num %d, current item count %d should be greater than %d", id, num, getCount(), num);
        return false;
    }

    public Item split(int count, boolean noSetNewId, Supplier<Long> newId, boolean sync) {                                                  //split an item and return a new one
        if (!this.decrease(count, sync))                                                                                                    //decrease count of the current item
            return null;
        Item splitted = this.clone();
        splitted.setParam(Params.count, count, false);                                                                                                    //set the count of the new item
        if (!noSetNewId)
            splitted.setId(newId.get(), false);                                                                                             //set a new id for the new item
        return splitted;                                                                                                                    //return a new item
    }

    @Override
    public Item clone() {                                                                                                                   //guess what?
        try {
            Item cloned = (Item)super.clone();                                                                                              //clone primitive fields by super.clone()
            cloned.included = new ArrayList<>();
            return cloned;
        }catch (CloneNotSupportedException e) {
            logger.error("can't clone item: %s", e.getMessage());
        }
        return null;
    }

    public boolean sync() {                                                                                                                 //force=true means sync the item anyway, whether needSync is true
        logger.info("syncing item %s", this);
        if (!ServerMain.sync(this)) {
            logger.error("can't sync item id %d", id);
            return false;
        }
        return true;
    }

    @Override
    public String toString() {return "Item{" + "id=" + id + ", txt='" + txt + '\'' + ", count=" + count + ", pid=" + pid + ", included=" + (Hibernate.isInitialized(included) ? included : "") + "}"; }
}
