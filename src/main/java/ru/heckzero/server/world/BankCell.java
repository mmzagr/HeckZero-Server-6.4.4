package ru.heckzero.server.world;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.Item;
import ru.heckzero.server.items.ItemBox;
import ru.heckzero.server.items.ItemTemplate;

import javax.persistence.*;
import java.time.Instant;
import java.util.StringJoiner;

@Entity(name = "BankCell")
@Table(name = "bank_cells")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "BankCell_Region")
public class BankCell {
    private static final Logger logger = LogManager.getFormatterLogger();

    public static Item createCell(Bank bank, int user_id, String password) {                                                                //create a bank cell and return a key item for that cell
        BankCell bankCell = new BankCell(bank.getId(), user_id, password);                                                                  //create a new bank cell
        if (!bankCell.sync())
            return null;
        Item key = ItemTemplate.getTemplateItem(ItemTemplate.BANK_KEY);                                                                     //generate a bank cell key item
        if (key == null)
            return null;
        key.setParam(Item.Params.txt, key.getParamStr(Item.Params.txt) + bankCell.getId(), false);                                          //set the key item params to make client display the key hint properly
        key.setParam(Item.Params.made, String.format("%s%d_%d_%d",  key.getParamStr(Item.Params.made), bank.getX(), bank.getY(), bank.getZ()), false);
        key.setParam(Item.Params.dt, bankCell.getDt(), false);
        key.setParam(Item.Params.hz, bankCell.getId(), false);
        key.setParam(Item.Params.res, bank.getTxt(), false);
        key.setParam(Item.Params.user_id, user_id, false);                                                                                  //user id this key belongs to
        key.setParam(Item.Params.section, 0, false);                                                                                        //user box sections this key will be placed to
        return key;
    }

    public static BankCell getBankCell(int id) {                                                                                            //try to get a Bank cell instance by building id
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<BankCell> query = session.createQuery("select c from BankCell c where c.id = :id", BankCell.class).setParameter("id", id).setCacheable(true);
            return query.getSingleResult();
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load bank cell with id %d from database: %s", id, e.getMessage());
        }
        return null;
    }

    @Transient ItemBox itemBox = null;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bank_cell_generator_sequence")
    @SequenceGenerator(name = "bank_cell_generator_sequence", sequenceName = "bank_cells_cell_id_seq", allocationSize = 1)
    private int id;                                                                                                                         //cell id generated by database sequence
    private int bank_id;                                                                                                                    //bank id
    private int user_id;                                                                                                                    //user id
    private String password;                                                                                                                //cell password
    private String email;                                                                                                                   //email for cell password restoration
    private long dt;                                                                                                                        //valid till date (epoch)
    private int block ;                                                                                                                     //cell is blocked

    protected BankCell() { }

    public BankCell(int bank_id, int user_id, String password) {
        this.bank_id = bank_id;
        this.user_id = user_id;
        this.password = password;
        this.email = StringUtils.EMPTY;
        this.dt = Instant.now().getEpochSecond() + ServerMain.ONE_MES * 4;                                                                  //now + 4 month, lease time in client will be shown as now + 1 month
        this.block = 0;                                                                                                                     //new cell is now blocked by default
        return;
    }

    public boolean isBlocked() {return block == 1;}                                                                                         //is the cell blocked
    public int getId()  {return id;}
    public long getDt() {return dt;}
    public String getPassword() {return password;}

    public ItemBox getItemBox() {return itemBox == null ? (itemBox = ItemBox.init(ItemBox.BoxType.BANK_CELL, id, true)) : itemBox;}          //get the building itembox, initialize if needed

    public boolean block() {this.block = 1; return sync();}                                                                                 //block the cell
    public boolean unblock() {this.block = 0; return sync();}                                                                               //unblock the cell

    public boolean sync() {                                                                                                                 //sync the bank cell
        if (!ServerMain.sync(this)) {
            logger.error("can't sync bank cell %s", this);
            return false;
        }
        logger.info("synced bank cell %s", this);
        return true;
    }

    public String cellXml() {                                                                                                                 //XML formatted bank data
        StringJoiner sj = new StringJoiner("", "<BK sell=\"1\">", "</BK>");
        sj.add(getItemBox().getXml());
        return sj.toString();
    }

    @Override
    public String toString() {return "BankCell{" + "id=" + id + ", bank_id=" + bank_id + ", user_id=" + user_id + '}'; }
}