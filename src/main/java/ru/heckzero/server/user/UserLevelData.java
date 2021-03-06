package ru.heckzero.server.user;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;
import ru.heckzero.server.ServerMain;

import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Cacheable
@Immutable
@Entity(name = "UserLevel")
@Table(name = "users_level")
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class UserLevelData {
    private static final Logger logger              = LogManager.getFormatterLogger();
    private static final List<UserLevelData> userLevels = new ArrayList<>();                                                                //store all data from db here for further usage
    private static final AtomicBoolean initialized  = new AtomicBoolean(false);

    @Id
    private int exp;
    private int level, stat, statup, maxrank, perkpoints;
    private int max_hp_man, max_psy_man, exp_pve_man, exp_pvp_man;
    private int max_hp_woman,   max_psy_woman,  exp_pve_woman,  exp_pvp_woman;
    private int quest_diff1, quest_diff2, quest_diff3, quest_diff4, quest_diff5;


    public UserLevelData() { }

    private static void ensureInitialized() {                                                                                               //make sure that the list of UserLevel objects is initialized
        if (initialized.compareAndSet(false, true)) {
            try (Session ses = ServerMain.sessionFactory.openSession()) {
                List<UserLevelData> levels = ses.createQuery("select u from UserLevel u", UserLevelData.class).list();
                userLevels.addAll(levels);
            } catch (Exception ex) {
                logger.error("can't load user level table: %s:%s", ex.getClass().getSimpleName(), ex.getMessage());
            }
            synchronized (userLevels) {
                userLevels.notifyAll();
            }
        }
    }

    private static UserLevelData getUserStatus(User usr) {                                                                                  //get curr User state (UserLevel) by his "exp" status
        ensureInitialized();
        synchronized (userLevels) {
            while (userLevels.isEmpty()) {
                try {
                    userLevels.wait();
                } catch (InterruptedException e) {
                    logger.error("interrupted in waiting fo the userLevels to initialize: %s:%s", e.getClass().getSimpleName(), e.getMessage());
                }
            }
        }

        int userExp = usr.getParamInt(User.Params.exp);
        return userLevels.stream()
                .filter(ulvl -> userExp >= ulvl.exp)
                .max(Comparator.comparingInt(o -> o.exp))
                .orElseGet(UserLevelData::new);
    }

    public static int getLevel(User u)      { return getUserStatus(u).level;        }
    public static int getStat(User u)       { return getUserStatus(u).stat;         }
    public static int getStatUp(User u)     { return getUserStatus(u).statup;       }
    public static int getMaxRank(User u)    { return getUserStatus(u).maxrank;      }
    public static int getPerkPoints(User u) { return getUserStatus(u).perkpoints;   }

    public static int getExpLastLvl(User u) { return getExpStatus(u.getParamInt(User.Params.level)); }
    public static int getExpNextLvl(User u) { return getExpStatus(u.getParamInt(User.Params.level) + 1); }

    public static int getMaxHP(User u)      { return u.getParamInt(User.Params.man) == 0 ? getUserStatus(u).max_hp_woman  : getUserStatus(u).max_hp_man;  }
    public static int getMaxPsy(User u)     { return u.getParamInt(User.Params.man) == 0 ? getUserStatus(u).max_psy_woman : getUserStatus(u).max_psy_man; }
    public static int getMaxExpPvE(User u)  { return u.getParamInt(User.Params.man) == 0 ? getUserStatus(u).exp_pve_woman : getUserStatus(u).exp_pve_man; }
    public static int getMaxExpPvP(User u)  { return u.getParamInt(User.Params.man) == 0 ? getUserStatus(u).exp_pvp_woman : getUserStatus(u).exp_pvp_man; }

    public static int getQuestDiffReward(User u, int diffLvl) {
        return switch (diffLvl) {
            default -> getUserStatus(u).quest_diff1;
            case 2  -> getUserStatus(u).quest_diff2;
            case 3  -> getUserStatus(u).quest_diff3;
            case 4  -> getUserStatus(u).quest_diff4;
            case 5  -> getUserStatus(u).quest_diff5;
        };
    }

    private static int getExpStatus(int userLvl) {
        return userLevels.stream().filter(ulvl -> ulvl.level == userLvl).min(Comparator.comparingInt(o -> o.exp)).orElseGet(UserLevelData::new).exp;
    }

    @Override
    public String toString() {
        return "UserLevel{"         +
                "exp="              + exp           +
                ", level="          + level         +
                ", stat="           + stat          +
                ", maxHPMan="       + max_hp_man    +
                ", maxPsyMan="      + max_psy_man   +
                ", expPvEMan="      + exp_pve_man   +
                ", expPvPMan="      + exp_pvp_man   +
                ", maxHPWoman="     + max_hp_woman  +
                ", maxPsyWoman="    + max_psy_woman +
                ", expPvEWoman="    + exp_pve_woman +
                ", expPvPWoman="    + exp_pvp_woman +
                ", maxrank="        + maxrank       +
                ", perkpoints="     + perkpoints    +
                ", statup="         + statup        +
                ", qestDiff1="      + quest_diff1   +
                ", qestDiff2="      + quest_diff2   +
                ", qestDiff3="      + quest_diff3   +
                ", qestDiff4="      + quest_diff4   +
                ", qestDiff5="      + quest_diff5   +
                '}';
    }
}