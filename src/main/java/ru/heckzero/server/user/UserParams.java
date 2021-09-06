package ru.heckzero.server.user;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.lang.reflect.Field;

@Embeddable
class UserParams {
    private static Logger logger = LogManager.getFormatterLogger();

    private String login, password, email;                                                                                                  //ask the Captain Obvious about that params
    private long reg_time;                                                                                                                  //user registration time (epoch)
    private long lastlogin;                                                                                                                 //user last login time, needed for computing new loc_time
    private long lastlogout;                                                                                                                //user last logout time epoch
    private long lastclantime;                                                                                                              //last time the user has left his clan
    private long loc_time;                                                                                                                  //allowed time to leave location (epoch)
    private long cure_time;                                                                                                                 //time of completion of treatment (epoch)
    private String god;                                                                                                                     //is user BoDun ?
    private int hint;                                                                                                                       //user custom settings used in PDA
    private int exp;                                                                                                                        //experience
    private int pro;                                                                                                                        //profession
    private double propwr;                                                                                                                  //profession power (rate) (0-1)
    private int rank_points;                                                                                                                //frags
    private String clan, clan_img;                                                                                                          //clan name and clan image
    private int clr;                                                                                                                        //chat font color
    private String img;                                                                                                                     //user avatar image in i/avatar client folder
    private String alliance;                                                                                                                //user's alliance
    private int man;                                                                                                                        //man - 1, woman - 0
    @Column(name = "\"HP\"")   private int HP;                                                                                              //Health Points (HP)
    private int psy;                                                                                                                        //psychic energy level
    private int stamina, str, dex, pow, acc, intel;                                                                                         //user "stats" stamina, strength, dexterity, accuracy, intellect
    @Column(name = "int")      private int intu;                                                                                            //intuition ("int" in db, "intu" here, cause the word "int" is reserved in java)
    private int sk0, sk1, sk2, sk3, sk4, sk5, sk6, sk7, sk8, sk9, sk10, sk11, sk12;                                                         //skills level
    @Column(name = "\"X\"")    private int X;                                                                                               //X coordinate
    @Column(name = "\"Y\"")    private int Y;                                                                                               //Y coordinate
    @Column(name = "\"Z\"")    private int Z;                                                                                               //Z - house number within a location
    private int hz;                                                                                                                         //hz - house type
    @Column(name = "\"ROOM\"") private int ROOM;                                                                                            //Z - room number within a house
    private long id1, id2;                                                                                                                  //items id generator borders
    private int i1;                                                                                                                         //item id generator cursor
    private String ne, ne2;                                                                                                                 //tell what it is
    private int cup_0, cup_1, cup_2, p78money;                                                                                              //copper and perk coins
    private double silv, gold;                                                                                                              //silver and gold coins
    private int acc_flags;                                                                                                                  //some account options, still not well known
    private String siluet;                                                                                                                  //siluet description
    private int bot;                                                                                                                        //0 - player is a human, 1- bot
    private String name, city, about, note;                                                                                                 //user self-given params in "about" section of PDA
    private String list;                                                                                                                    //user contact list
    private String plist;                                                                                                                   //perk list
    @Column(name = "\"ODratio\"") private int ODratio;                                                                                      //OD ratio affected on user
    private int virus;                                                                                                                      //biological infection
    private String brokenslots;                                                                                                             //broken slots list. each slot represents by a single letter
    private int poisoning;                                                                                                                  //poison level
    private int ill;                                                                                                                        //infection with the virus X (-1...1)
    private String illtime;                                                                                                                 //virus X infection duration
    private double sp_head, sp_left, sp_right, sp_foot;                                                                                     //slot damage
    private double eff1, eff2, eff3, eff4, eff5, eff6, eff7, eff8, eff9, eff10;                                                             //negative effects which affect the user
    private int rd;                                                                                                                         //reconstructions count made since last UP
    private int rd1;                                                                                                                        //stats available for free reconstruction
    private int t1, t2;                                                                                                                     //training 1,2 setting are for the initial quest
    private String dismiss;                                                                                                                 //user block reason, we treat the user as blocked if it's not empty
    private int chatblock, forumblock;                                                                                                      //time till user is blocked (banned) on chat or forum by moderator


    void setParam(User.Params paramName, Object paramValue) {                                                                               //set user param value
        try {
            Field field = this.getClass().getDeclaredField(paramName.toString());                                                           //find a field of name paramName
            Class fieldType = field.getType();                                                                                              //found field type
            Class valueType = paramValue.getClass();                                                                                        //value type
            if (fieldType.equals(valueType)) {                                                                                              //if they are equals, just set the value
                field.set(this, paramValue);
                return;
            }
            String strValue = paramValue instanceof String ? (String)paramValue : paramValue.toString();                                    //cast or convert paramValue to String
            switch (fieldType.getSimpleName()) {                                                                                            //a short field type name (Integer, String, etc.)
                case "String" -> field.set(this, strValue);                                                                                 //just set a String value to String field type
                case "int" -> field.set(this, NumberUtils.isParsable(strValue) ? Math.toIntExact(Math.round(Double.parseDouble(strValue))) : 0); //convert String value to field type
                case "long" -> field.set(this, NumberUtils.isParsable(strValue) ? Math.round(Double.parseDouble(strValue)) : 0L);
                case "double" -> field.set(this, NumberUtils.isParsable(strValue) ? Math.round(Double.parseDouble(strValue) * 1000D) / 1000D  : 0D);
                default -> logger.error("can't set param %s, param type '%s' is not supported", paramName, fieldType.getSimpleName());
            }
        } catch (NoSuchFieldException | SecurityException | IllegalAccessException e) {
            logger.warn("cannot set param %s to value %s: %s", paramName, paramValue, e.getMessage());
        }
        return;
    }

    Object getParam(User.Params paramName) throws NoSuchFieldException, IllegalAccessException {                                            //try to find a field with the name equals to paramName
        Field field = this.getClass().getDeclaredField(paramName.toString());
        return field.get(this);                                                                                                             //and return it
    }
}
