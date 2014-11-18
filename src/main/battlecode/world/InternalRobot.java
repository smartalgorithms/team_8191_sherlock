package battlecode.world;

import java.util.HashMap;
import java.util.Map;

import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.CommanderSkillType;
import battlecode.common.Team;
import battlecode.common.TerrainTile;
import battlecode.common.Upgrade;
import battlecode.engine.GenericRobot;
import battlecode.engine.signal.Signal;
import battlecode.server.Config;
import battlecode.world.signal.AttackSignal;
import battlecode.world.signal.BroadcastSignal;
import battlecode.world.signal.CaptureSignal;
import battlecode.world.signal.DeathSignal;
import battlecode.world.signal.RegenSignal;
import battlecode.world.signal.ResearchSignal;
import battlecode.world.signal.SelfDestructSignal;
import battlecode.world.signal.ShieldSignal;
import battlecode.world.signal.SpawnSignal;

public class InternalRobot extends InternalObject implements Robot, GenericRobot {

    protected volatile double myEnergonLevel;
    protected volatile double myShieldLevel;
    protected volatile double mySupplyLevel;
    protected volatile Direction myDirection;
    protected volatile boolean energonChanged = true;
    protected volatile boolean shieldChanged = true;
    protected volatile long controlBits;
    // is this used ever?
    protected volatile boolean hasBeenAttacked = false;
    private static boolean upkeepEnabled = Config.getGlobalConfig().getBoolean("bc.engine.upkeep");
    /**
     * number of bytecodes used in the most recent round
     */
    private volatile int bytecodesUsed = 0;

    public final RobotType type;

    private volatile HashMap<Integer, Integer> broadcastMap = new HashMap<Integer, Integer>();
    private boolean broadcasted = false;
    
    protected volatile boolean regen;
    private boolean upkeepPaid;
    
    private int researchRounds;
    private Upgrade researchUpgrade;
    
    private int miningRounds;
    private int defusingRounds;
    private MapLocation defusingLocation;
    private int capturingRounds;
    private RobotType capturingType;

    private Signal movementSignal;
    private Signal attackSignal;
    public ResearchSignal researchSignal;

    private int roundsSinceLastDamage;
    private int roundsSinceLastSpawn;
    private int roundsAlive;

    private boolean didSelfDestruct;

    private double timeUntilMovement;
    private double timeUntilAttack;
    private double loadingDelay;
    private double cooldownDelay;

    private int missileCount = 0;
    private int hatCount = 0;

    private int buildDelay;

    @SuppressWarnings("unchecked")
    public InternalRobot(GameWorld gw, RobotType type, MapLocation loc, Team t,
                         boolean spawnedRobot, int buildDelay) {
        super(gw, loc, t);
//        myDirection = Direction.values()[gw.getRandGen().nextInt(8)];
        this.type = type;
        this.buildDelay = buildDelay;

        myEnergonLevel = getMaxEnergon();
        if (type.isBuilding && type != RobotType.HQ && type != RobotType.TOWER) {
            myEnergonLevel /= 2.0;
        }
        myShieldLevel = 0.0;
        mySupplyLevel = 0.0;
        
        researchRounds = 0;
        researchUpgrade = null;
        
        miningRounds = 0;
        defusingRounds = 0;
        capturingRounds = 0;
        capturingType = null;;

        roundsSinceLastDamage = 0;
        roundsSinceLastSpawn = Integer.MAX_VALUE / 2;
        roundsAlive = 0;

//        incomingMessageQueue = new LinkedList<Message>();

        controlBits = 0;

        didSelfDestruct = false;
        
        timeUntilMovement = 0.0;
        timeUntilAttack = 0.0;
        loadingDelay = 0.0;
        cooldownDelay = 0.0;

        missileCount = 0;

        
    }

    public RobotInfo getRobotInfo() {
        return new RobotInfo(getID(), getTeam(), type, getLocation(), getTimeUntilMovement(), getTimeUntilAttack(), getEnergonLevel(), getSupplyLevel(), getXP(), getCapturingType() != null, getCapturingType(), getCapturingRounds(), getMissileCount());
    }
    
    public void clearResearching() {
    	researchRounds = 0;
    	researchUpgrade = null;
    }
    
    public void setResearching(Upgrade upgrade) {
		researchRounds = upgrade.numRounds;
    	researchUpgrade = upgrade;
    }
    
    public int getResearchRounds() {
    	return researchRounds;
    }
    
//    public void scanForMines() {
//    	MapLocation base = getLocation();
//    	Team t = getTeam().opponent();
//    	for (int dx=-1; dx<=1; dx++) {
//    		for (int dy=-1; dy<=1; dy++) {
//    			MapLocation loc = base.add(dx, dy);
//    			if(myGameWorld.getMine(loc) == t) myGameWorld.addKnownMineLocation(getTeam(), loc);
//    		}
//    	}
//    	addAction(new ScanSignal(this));
//    }

    public void decrementMissileCount() {
        missileCount--;
    }

    public int getMissileCount() {
        return missileCount;
    }

    public void incrementHatCount() {
        hatCount++;
    }

    public int getHatCount() {
        return hatCount;
    }

    public void addAction(Signal s) {
        myGameWorld.visitSignal(s);
    }

    public void addTimeUntilMovement(double time) {
        timeUntilMovement += time;
    }

    public void addTimeUntilAttack(double time) {
        timeUntilAttack += time;
    }

    public void addCooldownDelay(double delay) {
        cooldownDelay += delay;
    }

    public void addLoadingDelay(double delay) {
        loadingDelay += delay;
    }

    public void decrementDelays() {
        timeUntilAttack--;
        timeUntilMovement--;
        loadingDelay--;
        cooldownDelay--;
        if (timeUntilAttack < 0.0) {
            timeUntilAttack = 0.0;
        }
        if (timeUntilMovement < 0.0) {
            timeUntilMovement = 0.0;
        }
        if (loadingDelay < 0.0) {
            loadingDelay = 0.0;
        }
        if (cooldownDelay < 0.0) {
            cooldownDelay = 0.0;
        }
    }

    public double getTimeUntilMovement() {
        return Math.max(timeUntilMovement, loadingDelay);
    }

    public double getTimeUntilAttack() {
        return Math.max(timeUntilAttack, cooldownDelay);
    }

    public double getAttackDelay() {
        return timeUntilAttack;
    }

    public double getMovementDelay() {
        return timeUntilMovement;
    }

    public double getLoadingDelay() {
        return loadingDelay;
    }

    public double getCooldownDelay() {
        return cooldownDelay;
    }
    
    public Upgrade getResearchingUpgrade() {
    	return researchUpgrade;
    }
    
    public boolean canExecuteCode() {
    	if (getEnergonLevel() <= 0.0) return false;
        if (type.isBuilding && type != RobotType.HQ && type != RobotType.TOWER && roundsAlive < buildDelay) return false;
    	return true;
    }

    public boolean isActive() {
        if (type.isBuilding && type != RobotType.HQ && type != RobotType.TOWER && roundsAlive < buildDelay) {
            return false;
        } else {
            return true;
        }
    }

    public void resetSpawnCounter() {
        roundsSinceLastSpawn = 0;
    }

    @Override
    public void processBeginningOfRound() {
        super.processBeginningOfRound();
    }

    public void processBeginningOfTurn() {
        decrementDelays();
    	
        if (type == RobotType.COMMANDER && ((InternalCommander)this).hasSkill(CommanderSkillType.REGENERATION)) {
           this.changeEnergonLevel(1); 
        }

        if (canExecuteCode() && type.supplyUpkeep > 0) {
            upkeepPaid = mySupplyLevel > (Math.max(type.bytecodeLimit - 2000, 0) / 1000.0);
            if (upkeepPaid) {
                decreaseSupplyLevel(Math.max(type.bytecodeLimit - 2000, 0) / 1000.0);
            }
        } else {
            upkeepPaid = true;
        }
    }

    @Override
    public void processEndOfTurn() {
        super.processEndOfTurn();
        
        // autosend aggregated broadcast
        if (broadcasted) myGameWorld.visitSignal(new BroadcastSignal(this, broadcastMap));
        
    	broadcastMap = new HashMap<Integer, Integer>();
        broadcasted = false;
       
        if (type != RobotType.HQ) { 
            roundsSinceLastDamage++;
        } else {
            roundsSinceLastSpawn++;
        }

        // refund supply
        if (upkeepPaid) {
            double supplyPaid = Math.max(type.bytecodeLimit - 2000, 0) / 1000.0;
            double supplyNeeded = Math.max(getBytecodesUsed() - 2000, 0) / 1000.0;
            increaseSupplyLevel(supplyPaid - supplyNeeded);
        }

        if (type != RobotType.HQ && type != RobotType.SUPPLYDEPOT) {
            mySupplyLevel *= (1 - GameConstants.SUPPLY_DECAY);
        }

        // generate supply
        if ((type == RobotType.SOLDIER || type == RobotType.BASHER) && myGameWorld.hasUpgrade(getTeam(), Upgrade.CONTROLLEDECOPHAGY)) {
            if (type == RobotType.SOLDIER) {
                increaseSupplyLevel(5);
            } else if (type == RobotType.BASHER) {
                increaseSupplyLevel(10);
            }
        }

        if (type == RobotType.SUPPLYDEPOT) {
            increaseSupplyLevel(100);
        }

        roundsAlive++;
        // after building is done, double health
        if (type.isBuilding && roundsAlive == buildDelay && type != RobotType.HQ && type != RobotType.TOWER) {
            changeEnergonLevel(getEnergonLevel());
            // increase robot count
            myGameWorld.incrementRobotTypeCount(getTeam(), type);
        } else if (!type.isBuilding && roundsAlive == 1) {
            myGameWorld.incrementRobotTypeCount(getTeam(), type);
        }

        if (roundsAlive % GameConstants.MISSILE_SPAWN_FREQUENCY == 0 && type == RobotType.LAUNCHER) {
            missileCount = Math.min(missileCount + 1, GameConstants.MAX_MISSILE_COUNT);
        }

        if (type == RobotType.MISSILE && roundsAlive >= 5) {
            suicide();
        }
        
        if (movementSignal != null) {
            myGameWorld.visitSignal(movementSignal);
            movementSignal = null;
        }

        if (researchSignal != null) {
            if (!myGameWorld.hasUpgrade(getTeam(), researchSignal.getUpgrade())) {
                myGameWorld.visitSignal(researchSignal);
            } else {
                researchSignal = null;
            }
        }
        
        if (attackSignal != null) {
        	myGameWorld.visitSignal(attackSignal);
        	attackSignal = null;
        }
    }

    @Override
    public void processEndOfRound() {
        super.processEndOfRound();
    }

    public double getEnergonLevel() {
        return myEnergonLevel;
    }
    
    public double getShieldLevel() {
    	return myShieldLevel;
    }

    public int getXP() {
        if (type == RobotType.COMMANDER) {
            System.out.println(this);
            System.out.println( (InternalCommander)this);
            return ((InternalCommander)this).getXP();
        }
        return 0;
    }

    public double getSupplyLevel() {
        return mySupplyLevel;
    }

    public void decreaseSupplyLevel(double dec) {
        mySupplyLevel -= dec;
    }

    public void increaseSupplyLevel(double inc) {
        mySupplyLevel += inc;
    }

    public Direction getDirection() {
        return myDirection;
    }

//    there is no regen, healing by medbay is immediate
//    public void setRegen() {
//        if (type != RobotType.TOWER || !myGameWorld.timeLimitReached())
//            regen = true;
//    }

    public boolean getRegen() {
        return regen;
    }

    // includes buffs
    public int getAttackRadiusSquared() {
        int base = type.attackRadiusSquared;
        if (type == RobotType.HQ && myGameWorld.getRobotTypeCount(getTeam(), RobotType.TOWER) >= 2) {
            return GameConstants.ATTACK_RADIUS_SQUARED_BUFFED_HQ;
        } else {
            return base;
        }
    }

    public int getMovementDelayForType() {
        if (type == RobotType.BASHER && myGameWorld.hasUpgrade(getTeam(), Upgrade.REGENERATIVEMACHINERY)) {
            return 1;
        } else {
            return type.movementDelay;
        }
    }

    public int getLoadingDelayForType() {
        if (type == RobotType.SOLDIER && myGameWorld.hasUpgrade(getTeam(), Upgrade.NEUROMORPHICS)) {
            return 0;
        } else {
            return type.loadingDelay;
        }
    }

    public int getCooldownDelayForType() {
        if (type == RobotType.SOLDIER && myGameWorld.hasUpgrade(getTeam(), Upgrade.NEUROMORPHICS)) {
            return 0;
        } else {
            return type.cooldownDelay;
        }
    }

    public void takeDamage(double baseAmount) {
        if (baseAmount < 0) {
            changeEnergonLevel(-baseAmount);
        } else {
            if (baseAmount > 0) {
                roundsSinceLastDamage = 0;
            }
            // HQ has a tower boost
            double rate = 1.0;
            if (type == RobotType.HQ) {
                int towerCount = myGameWorld.getRobotTypeCount(getTeam(), RobotType.TOWER);
                if (towerCount >= 6) {
                    rate = 0.3;
                } else if (towerCount >= 4) {
                    rate = 0.5;
                } else if (towerCount >= 1) {
                    rate = 0.8;
                }
            }
            changeEnergonLevelFromAttack(-rate * baseAmount);
        }
    }
    
    public void takeShieldedDamage(double baseAmount) {
        if (baseAmount < 0) {
        	changeShieldLevel(-baseAmount);
        } else {
            double remainder = changeShieldLevelFromAttack(-baseAmount);
            changeEnergonLevelFromAttack(-remainder);
        }
    }

    public void takeDamage(double amt, InternalRobot source) {
        // uncomment this to test immortal base nodes
        //if(type==RobotType.TOWER&&myGameWorld.towerToNode(this).isPowerCore())
        //	return;
    	// make sure encampments don't take damage
        if (!(getTeam() == Team.NEUTRAL))
        {
        	//if (source.type == RobotType.ARTILLERY)
        	//	takeShieldedDamage(amt);
        	//else
                takeDamage(amt);
        }
    }
    
    public double changeShieldLevelFromAttack(double amount) {
        hasBeenAttacked = true;
        return changeShieldLevel(amount);
    }
    
    public void changeEnergonLevelFromAttack(double amount) {
        hasBeenAttacked = true;
        changeEnergonLevel(amount);
    }
    
    public double changeShieldLevel(double amount) {
        myShieldLevel += amount;
        /*if (myShieldLevel > GameConstants.SHIELD_CAP) {
        	myShieldLevel = GameConstants.SHIELD_CAP;
          }*/
        shieldChanged = true;

        if (myShieldLevel <= 0) {
        	double diff = -myShieldLevel;
        	myShieldLevel = 0;
        	return diff;
        }
        return 0.0;
    }

    public void changeEnergonLevel(double amount) {
        myEnergonLevel += amount;
        if (myEnergonLevel > getMaxEnergon()) {
            myEnergonLevel = getMaxEnergon();
        }
        energonChanged = true;

        if (myEnergonLevel <= 0 && getMaxEnergon() != Integer.MAX_VALUE) {
            processLethalDamage();
        }
    }

    public void processLethalDamage() {
        myGameWorld.notifyDied(this);
    }

    public boolean clearEnergonChanged() {
        boolean wasChanged = energonChanged;
        energonChanged = false;
        return wasChanged;
    }
    
    public boolean clearShieldChanged() {
        boolean wasChanged = shieldChanged;
        shieldChanged = false;
        return wasChanged;
    }

    public double getMaxEnergon() {
        return type.maxHealth;
    }

    public double calculateMovementActionDelay(MapLocation from, MapLocation to, TerrainTile terrain) {
        double base = 1;
        if (from.distanceSquaredTo(to) <= 1) {
            base = getMovementDelayForType();
        } else {
            base = getMovementDelayForType() * 1.4;
        }
        return base;
    }

    public double calculateAttackActionDelay(RobotType r) {
        if (r == RobotType.HQ && myGameWorld.getRobotTypeCount(getTeam(), RobotType.TOWER) >= 5) {
            return r.attackDelay / 2;
        }
        return r.attackDelay;
    }

    public void activateResearch(ResearchSignal s, double attackDelay, double movementDelay) {
        addLoadingDelay(attackDelay);
        addTimeUntilMovement(movementDelay);

        researchSignal = s;
    }

    public void activateMovement(Signal s, double attackDelay, double movementDelay) {
        movementSignal = s;
        addLoadingDelay(attackDelay);
        addTimeUntilMovement(movementDelay);
    }
    
    public void activateAttack(Signal s, double attackDelay, double movementDelay) {
        attackSignal = s;
        addTimeUntilAttack(attackDelay);
        addCooldownDelay(movementDelay);
    }

    public void addBroadcast(int channel, int data) {
    	broadcastMap.put(channel, data);
        broadcasted = true;
    }
    
    public void activateMinelayer(Signal s, int delay) {
    	myGameWorld.visitSignal(s);
    	miningRounds = delay;
    }
    
    public void activateMinestop(Signal s, int delay) {
    	myGameWorld.visitSignal(s);
    	miningRounds = 0;
    }
    
    public void activateDefuser(Signal s, int delay, MapLocation target) {
    	myGameWorld.visitSignal(s);
    	defusingRounds = delay;
    	defusingLocation = target;
    }
   
    /* 
    public void activateCapturing(CaptureSignal s, int delay) {
    	myGameWorld.visitSignal(s);
    	capturingRounds = delay;
        addActionDelay(delay);
    	capturingType = s.getType();
    }
    */

    public int getMiningRounds() {
    	return miningRounds;
    }
    
    public int getDefusingRounds() { 
    	return defusingRounds;
    }

    public boolean hasBroadcasted() {
        return broadcasted;
    }
    
    public void setLocation(MapLocation loc) {
    	MapLocation oldloc = getLocation();
        super.setLocation(loc);
    }

    public void setDirection(Direction dir) {
        myDirection = dir;
    }

    public void setSelfDestruct() {
        didSelfDestruct = true;
    }

    public void suicide() {
        if (didSelfDestruct) {
            (new SelfDestructSignal(this, getLocation())).accept(myGameWorld);
        }
        (new DeathSignal(this)).accept(myGameWorld);
    }

//    public void enqueueIncomingMessage(Message msg) {
//        incomingMessageQueue.add(msg);
//    }
//
//    public Message dequeueIncomingMessage() {
//        if (incomingMessageQueue.size() > 0) {
//            return incomingMessageQueue.remove(0);
//        } else {
//            return null;
//        }
//        // ~ return incomingMessageQueue.poll();
//    }
//
//    public Message[] dequeueIncomingMessages() {
//        Message[] result = incomingMessageQueue.toArray(new Message[incomingMessageQueue.size()]);
//        incomingMessageQueue.clear();
//        return result;
//    }
    
    public int getCapturingRounds() {
    	return capturingRounds;
    }
    
    public RobotType getCapturingType() {
    	return capturingType;
    }

    public void setControlBits(long l) {
        controlBits = l;
    }

    public long getControlBits() {
        return controlBits;
    }

    public void setBytecodesUsed(int numBytecodes) {
        bytecodesUsed = numBytecodes;
    }

    public int getBytecodesUsed() {
        return bytecodesUsed;
    }

    public int getBytecodeLimit() {
        return canExecuteCode() ? (upkeepPaid ? type.bytecodeLimit : type.bytecodeLimit / 2) : 0;
    }

    public boolean hasBeenAttacked() {
        return hasBeenAttacked;
    }

    @Override
    public String toString() {
        return String.format("%s:%s#%d", getTeam(), type, getID());
    }

    public void freeMemory() {
//        incomingMessageQueue = null;
        movementSignal = null;
        attackSignal = null;
    }
}
