package ru.heckzero.server.items;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ItemBox {
    private static final Logger logger = LogManager.getFormatterLogger();
    public enum boxType {USER, BUILDING}

    public static final EnumSet<Item.Params> userParams = EnumSet.of(Item.Params.user_id, Item.Params.section, Item.Params.slot);
    public static final EnumSet<Item.Params> bldParams = EnumSet.of(Item.Params.b_id);

    private final CopyOnWriteArrayList<Item> items = new CopyOnWriteArrayList<>();
    private boolean needSync = false;

    public static ItemBox init(boxType boxType, int id, boolean needSync) {
        ItemBox itemBox = new ItemBox(needSync);                                                                                            //needSync - if a returned ItemBox has to sync its items with a db
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Item> query = session.createNamedQuery(String.format("ItemBox_%s", boxType.toString()), Item.class).setParameter("id", id).setCacheable(true);
            List<Item> items = query.list();
            itemBox.load(items);
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load ItemBox type %s by id %d from database: %s:%s, an empty ItemBox will be returned", boxType, id, e.getClass().getSimpleName(), e.getMessage());
        }
        return itemBox;
    }

    public ItemBox() { }
    public ItemBox(boolean needSync) {this.needSync = needSync;}
    public ItemBox(ItemBox box) {box.items.forEach(i -> this.addItem(i.clone()));}                                                          //create an ItemBox by doing deep copy of items from box
    public ItemBox(ItemBox box, boolean needSync) {this(box); this.needSync = needSync;}

    public boolean isEmpty() {return items.isEmpty();}

    public void load(List<Item> items) {
        List <Item> included = items.stream().filter(Item::isIncluded).toList();                                                            //get all child items in the list
        for (Item child : included) {
            Item parent = items.stream().filter(i -> i.getId() == child.getPid()).findFirst().orElseGet(Item::new);                         //find parent item for each child item
            parent.getIncluded().addItem(child);                                                                                            //add child into parent included
        }
        items.removeAll(included);                                                                                                          //remove all child items from the list course they all are included in their parents
        this.items.addAll(items);
        return;
    }
    public int size() {return items.size();}


    public boolean addItem(Item item)  { return this.items.addIfAbsent(item) && (!needSync || item.sync());}                                //add one item to this ItemBox
    public boolean addAll(ItemBox box) {return box.items.stream().map(this::addItem).allMatch(Predicate.isEqual(true));}                    //add all items(shallow copy) from box to this ItemBox

//    public List<Item> getItems()    {return items;}                                                                                         //get the 1st level items
    public List<Long> itemsIds() {return items.stream().mapToLong(Item::getId).boxed().toList();}                                           //get items IDs of the 1st level items


    public String getXml()        {return items.stream().map(Item::getXml).collect(Collectors.joining());}                                  //get XML list of items as a list of <O/> nodes with the included items
    public Item findFist() {return items.stream().findFirst().orElse(null);}
    public Item findItem(long id) {return items.stream().map(i -> i.findItem(id)).filter(Objects::nonNull).findFirst().orElse(null);}       //find an item recursively inside the item box
    public ItemBox findItems(Predicate<Item> predicate) {return items.stream().map(i -> i.findItems(predicate)).collect(ItemBox::new, ItemBox::addAll, ItemBox::addAll);}
    public ItemBox findExpired()  {return findItems(Item::isExpired);}                                                                      //get all expired items with included ones placed in a 1st level


    public void move(long id, ItemBox dstBox, EnumSet<Item.Params> rstParams, Map<Item.Params, Object> setParams) {
//        Item item getSplitItem(id, )
    }

    public boolean delItem(long id, int count) {
        Item item = findItem(id);
        if (item == null) {
            logger.error("can't delete item id %d because it was not found in itembox", id);
            return false;
        }
        if (count > 0 && count < item.getCount()) {                                                                                         //we should decrease an Item by count
            if (item.decrease(count))
                return !needSync || item.sync();
            else
                return false;
        }
        return delItem(id);                                                                                                                 //delete the entire item
    }

    public boolean delItem(long id) {                                                                                                       //delete an item from the box or from the parent's included item box
        Item item = findItem(id);                                                                                                           //try to find an item by id
        if (item == null) {
            logger.error("can't delete item id %d because it was not found in itembox", id);
            return false;
        }
        if (!item.findItems(Item::isNoTransfer).isEmpty()) {                                                                                //deleting is forbidden, item or one of its included has nt = 1
            logger.info("can't delete item id %d, because it or one of its included has no transfer flag set");
            return false;
        }

        if (item.isIncluded()) {                                                                                                            //the item is a child item
            Item parent = findItem(item.getPid());                                                                                          //try to find an item's master item by pid
            if (parent == null) {
                logger.error("can't delete item id %d because it's included and item's parent item id %d was not found", item.getPid(), id);
                return false;
            }
            if (!parent.getIncluded().items.remove(item)) {                                                                                 //remove the item from the parent's included item box
                logger.error("can't delete item id %d from parent item id %d, the parent included items do not contain the item", id, parent.getId());
                return false;
            }
        }else if (!items.remove(item)) {                                                                                                    //it's a 1st level item, remove it from this item box
            logger.error("can't remove item id %d from the item box because the item was not found in 1st level item list");
            return false;
        }
        return !needSync || Item.delFromDB(id, true);                                                                                       //delete the item from database with it's included
    }

    public Item getSplitItem(long id, int count, boolean noSetNewId, Supplier<Long> newId, EnumSet<Item.Params> rstParams) {                //find an Item and split it by count or just return it back, may be with a new id, which depends on noSetNewId argument and the item type
        Item item = getSplitItem(id, count, noSetNewId, newId);
        if (item == null)
            return null;
        rstParams.forEach(item::resetParam);
        return item;
    }

    public Item getSplitItem(long id, int count, boolean noSetNewId, Supplier<Long> newId) {                                                //find an Item and split it by count or just return it back, may be with a new id, which depends on noSetNewId argument and the item type
        Item item = findItem(id);                                                                                                               //find an item by id
        if (item == null) {                                                                                                                 //we couldn't find an item by id
            logger.error("can't find item id %d in the item box", id);
            return null;
        }
        if (count > 0 && count < item.getCount()) {                                                                                         //split the item
            logger.debug("splitting the item %d by cloning and decreasing by count %d", id, count);
            Item splitted = item.split(count, noSetNewId, newId);                                                                           //get a cloned item with a new ID and requested count
            return needSync ? (item.sync() ? splitted : null) : splitted;
        }
        logger.debug("get the entire item id %d stack of count %d", id, item.getCount());
        if (!delItem(id))
            return null;

        if (item.getCount() > 0 && !noSetNewId && item.getParamDouble(Item.Params.calibre) > 0)                                             //set a new id for the ammo
            item.setId(newId.get());

        logger.debug("returning item %s", item);
        return item;
    }

    public Item getClonnedItem(long id, int requestCount, Supplier<Long> newId) {
        Item item = findItem(id);                                                                                                               //item doesn't exist on warehouse yet
        if (item == null || !ServerMain.refresh(item)) {                                                                                    //after refreshing we know if the item exists in database and the actual item count
            logger.info("can't find an item id %d in  item box, the item doesn't exist anymore", id);
            return null;
        }
        Item clonnedItem = item.clone();
        clonnedItem.setId(newId.get());

        if (requestCount > 0 && requestCount < item.getCount()) {                                                                           //we are taking just a piece of item
            logger.info("taking a part of item %d of count %d", item.getId(), requestCount);
            clonnedItem.setCount(requestCount);
            item.decrease(requestCount);
            if (needSync)
                item.sync();
        }else {                                                                                                                             //we are taking the entire item
            logger.info("will take a whole stack and delete item %d", item.getId());
            delItem(id);                                                                                                                    //del the item from item box
            if (needSync)                                                                                                                   //del the item from database
                Item.delFromDB(id, true);
        }
       return clonnedItem;
    }

    public Item findJoinableItem(Item sample) {                                                                                             //find a joinable item in the itembox by a sample item
        Predicate<Item> isResEquals = i -> sample.isRes() && i.getParamInt(Item.Params.massa) == sample.getParamInt(Item.Params.massa);     //the sample is res and items weight is equals
        Predicate<Item> isDrugEquals = i -> sample.isDrug()&& i.getParamDouble(Item.Params.type) == sample.getParamInt(Item.Params.type);
        Predicate<Item> isSameName = i -> i.getParamStr(Item.Params.name).equals(sample.getParamStr(Item.Params.name));                     //items have the same name parameter value
        Predicate<Item> isJoinable = isSameName.and(isResEquals.or(isDrugEquals));                                                          //can items be joined

        return items.stream().filter(isJoinable).findFirst().orElse(null);                                                                  //iterate over the 1st level items
    }

    public boolean changeOne(long id, Item.Params paramName, Object paramValue) {
        Item item = findItem(id);
        if (item == null) {                                                                                                                 //we couldn't find an item by id
            logger.error("can't find item id %d in the item box", id);
            return false;
        }
        if (!item.setParam(paramName, paramValue))
            return false;
        return !needSync || item.sync();
    }

    public void forEach(Consumer<Item> action) {items.forEach(action); }


    public boolean sync() {                                                                                                                    //will sync all items with db only if needSync = true
        return !needSync || items.stream().map(Item::sync).allMatch(Predicate.isEqual(true));
    }                                                                                                                                       //recursively sync all items in the ItemBox

    @Override
    public String toString() {return "ItemBox{" + "items=" + items + '}'; }
}
