package ru.heckzero.server.user;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.AttributeKey;
import io.netty.util.internal.StringUtil;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Calendar;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Entity(name = "User")
@Table(name = "users")
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class User {
    public enum ChannelType {NOUSER, GAME, CHAT}                                                                                            //user channel type, set on login by online() and chatOn() methods
    public enum Params {login, password, email, reg_time, lastlogin, lastlogout, lastclantime, loc_time, cure_time, bot, clan, dismiss, nochat, siluet}  //all available params that can be accessed via get/setParam()
    public enum GetMeParams {time, tdt, login, email, loc_time, cure_time, god, hint, exp, pro, propwr, rank_points, clan, clr, img, alliance, man, HP, psy, maxHP, maxPsy, stamina, str, dex, INT, pow, acc, intel, X, Y, Z, hz}

    private static final Logger logger = LogManager.getFormatterLogger();

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generator_sequence")
    @SequenceGenerator(name = "generator_sequence", sequenceName = "users_id_seq", allocationSize = 1)
    private Integer id;

    @Embedded
    private final UserParams params = new UserParams();

    @Transient private Channel gameChannel = null;                                                                                          //user game socket
    @Transient private Channel chatChannel = null;                                                                                          //user chat socket

    public Channel getGameChannel() { return this.gameChannel;}
    public Channel getChatChannel() { return this.chatChannel;}

    public User() { }                                                                                                                       //default constructor
    public void setId(Integer id) {this.id = id;}

    public boolean isEmpty() {return id == null;}                                                                                           //user is empty (having empty params)
    public boolean isOnlineGame() {return gameChannel != null;}                                                                             //this user has a game channel assigned
    public boolean isOnlineChat() {return chatChannel != null;}                                                                             //this user has a chat channel assigned
    public boolean isMuted() {return isOnlineChat() && getParamInt(Params.nochat) == 0;}                                                    //this user is online and having it's chat on
    public boolean isBot() {return !getParamStr(Params.bot).isEmpty();}                                                                     //user is a bot (not a human)
    public boolean isCop() {return getParam(Params.clan).equals("police");}                                                                 //user is a cop (is a member of police clan)
    public boolean isInBattle() {return false;}                                                                                             //just a stub yet

    public String getLogin() {return getParamStr(Params.login);}                                                                            //just a shortcut
    private Long getParam_time() {return Instant.now().getEpochSecond();}                                                                   //always return epoch time is seconds
    private Integer getParam_tdt() {return Calendar.getInstance().getTimeZone().getOffset(Instant.now().getEpochSecond() / 3600L);}         //user time zone, used in user history log
    public Integer getParamInt(Params param) {return NumberUtils.toInt(getParamStr(param));}
    public Long getParamLong(Params param) {return NumberUtils.toLong(getParamStr(param));}
    public Double getParamDouble(Params param) {return NumberUtils.toDouble(getParamStr(param));}
    public String getParamStr(Params param) {return getParam(param).toString();}
    private Object getParam(Params param) {                                                                                                 //get user param value (param must be in Params enum)
        String paramName = param.name();                                                                                                    //param name as a String
        try {                                                                                                                               //try to find param in UserParam instance
            return params.getParam(paramName);
        } catch (Exception e) {logger.debug("cannot find param in UserParams params, gonna look for a special method to compute that param");}

        String methodName = String.format("getParam_%s", paramName);
        try {                                                                                                                               //if not found in params. try to compute the param value via the dedicated method
            Method method = this.getClass().getDeclaredMethod(methodName);
            return (method.invoke(this));                                                                                                   //always return string value
        } catch (Exception e) {
            logger.warn("cannot find or compute param %s, neither in User params nor by a dedicated method: %s", paramName, e.getMessage());
        }
        return new Object();
    }
    public void setParam(Params paramName, String paramValue) {setParam(paramName, (Object)paramValue);}                                    //set param to String value
    public void setParam(Params paramName, Integer paramValue) {setParam(paramName, (Object)paramValue);}                                   //set param to Integer value
    public void setParam(Params paramName, Long paramValue) {setParam(paramName, (Object)paramValue);}                                      //set param to Long value
    public void setParam(Params paramName, Double paramValue) {setParam(paramName, (Object)paramValue);}                                    //set param to Double value
    private void setParam(Params param, Object value) {this.params.setParam(param.name(), value);}                                          //universal method to set a param


    void online(Channel ch) {
        this.gameChannel = ch;                                                                                                              //set user game channel
        this.gameChannel.attr(AttributeKey.valueOf("chType")).set(ChannelType.GAME);                                                        //set the user channel type to GAME
        setParam(Params.lastlogin, Instant.now().getEpochSecond());                                                                         //set user last login time, needed to compute loc_time
        setParam(Params.nochat, 1);                                                                                                         //set initial user chat status to off, until 2nd chat connection completed
        return;
    }
    void offline() {
        logger.debug("setting user %s offline", getLogin());
        this.gameChannel = null;
        try {this.chatChannel.close(); }                                                                                                    //try to disconnect user chat channel
            catch (Exception e) {logger.error("can't close chat channel of user %s: %s", getLogin(), e.getMessage());}

        logger.info("user %s logged of the game", getLogin());
        return;
    }

    void chatOn(Channel ch) {
        this.chatChannel = ch;
        this.chatChannel.attr(AttributeKey.valueOf("chType")).set(ChannelType.CHAT);
        setParam(Params.nochat, 0);
        return;
    }
    void chatOff() {
        logger.info("turning user %s chat off", getLogin());
        this.chatChannel = null;
        setParam(Params.nochat, 1);
        return;
    }

    public void com_MYPARAM() {
        logger.info("processing <GETME/> from %s", gameChannel.attr(AttributeKey.valueOf("chStr")).get());
        String xml = String.format("<MYPARAM login=\"%s\" X=\"0\" Y=\"0\" Z=\"0\"></MYPARAM>", getParam(Params.login));
        sendMsg(xml);
        return;
    }
    public void com_GOLOC() {
        logger.info("processing <GOLOC/> from %s", gameChannel.attr(AttributeKey.valueOf("chStr")).get());
        String xml = String.format("<GOLOC><L/></GOLOC>");
        sendMsg(xml);
        return;
    }

    public void com_SILUET(String slt, String set) {
        logger.info("processing <SILUET/> from %s", gameChannel.attr(AttributeKey.valueOf("chStr")));
        logger.info("slt = %s, set = %s", slt, set);
        setParam(Params.siluet, set);
        String response = String.format("<SILUET code=\"0\"/><MYPARAM siluet=\"%s\"/>",  set);
        sendMsg(response);
        return;
    }

    public void sendMsg(String msg) {
        try {
            gameChannel.writeAndFlush(msg);
            return;
        }catch (Exception e) {
            logger.warn("cant send message %s to %s: %s", msg, getLogin(), e.getMessage());
        }
        return;
    }

    public void sendChatMsg(String msg) {
        try {
            chatChannel.writeAndFlush(msg);
            return;
        }catch (Exception e) {
            logger.warn("cant send message %s to %s: %s", msg, getLogin(), e.getMessage());
        }
        return;
    }
}
