package dev.realjr.pvpbot;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import java.util.UUID;

public final class SingleplayerPvpBot implements ModInitializer {
 enum Mode {SWORD,MACE,SMP,ELYTRA_MACE,SPEAR_MACE}
 static class Fight {
   MinecraftServer server; ServerLevel world; UUID owner; Husk bot; Mode mode;
   int time, lastAbility=-300, launch; boolean diving, spear;
   Fight(MinecraftServer s,ServerLevel w,ServerPlayer p,Husk b,Mode m){
     server=s; world=w; owner=p.getUUID(); bot=b; mode=m; spear=m==Mode.SPEAR_MACE;
   }
 }
 static Fight active;
 static Mode lastMode;
 static int wins, losses, difficulty=1;
 static final double[] HP={20,30,44}, DAMAGE={2.5,4.5,6.5}, SPEED={0.24,0.31,0.37};
 static final int[] COOLDOWN={140,100,72};
 @Override public void onInitialize(){
   CommandRegistrationCallback.EVENT.register((d,access,env)->register(d));
   ServerTickEvents.END_SERVER_TICK.register(SingleplayerPvpBot::tick);
   ServerLifecycleEvents.SERVER_STOPPED.register(s->{active=null;lastMode=null;wins=losses=0;});
 }
 static void register(CommandDispatcher<CommandSourceStack> d){
   var root=Commands.literal("pvpbot").executes(c->help(c.getSource()));
   root.then(Commands.literal("help").executes(c->help(c.getSource())));
   root.then(Commands.literal("stop").executes(c->stop(c.getSource())));
   root.then(Commands.literal("reset").executes(c->reset(c.getSource())));
   root.then(Commands.literal("kit").executes(c->kit(c.getSource())));
   root.then(Commands.literal("status").executes(c->status(c.getSource())));
   var dif=Commands.literal("difficulty");
   dif.then(Commands.literal("easy").executes(c->setDifficulty(c.getSource(),0)));
   dif.then(Commands.literal("normal").executes(c->setDifficulty(c.getSource(),1)));
   dif.then(Commands.literal("hard").executes(c->setDifficulty(c.getSource(),2)));
   root.then(dif);
   for(Mode m:Mode.values()) root.then(Commands.literal(m.name().toLowerCase()).executes(c->start(c.getSource(),m)));
   root.then(Commands.literal("elytramace").executes(c->start(c.getSource(),Mode.ELYTRA_MACE)));
   root.then(Commands.literal("spearmace").executes(c->start(c.getSource(),Mode.SPEAR_MACE)));
   d.register(root);
 }
 static ServerPlayer player(CommandSourceStack src){
   if(!src.getServer().isSingleplayer()){src.sendFailure(Component.literal("Only singleplayer supported."));return null;}
   if(src.getEntity() instanceof ServerPlayer p) return p;
   src.sendFailure(Component.literal("Use this command in-game."));return null;
 }
 static void msg(ServerPlayer p,String x){p.sendSystemMessage(Component.literal("[PvP Bot] "+x));}
 static int help(CommandSourceStack s){
   ServerPlayer p=player(s); if(p==null)return 0;
   msg(p,"Modes: /pvpbot sword | mace | smp | elytra_mace | spear_mace");
   msg(p,"Commands: kit | reset | stop | status | difficulty easy/normal/hard");return 1;
 }
 static int status(CommandSourceStack s){
   ServerPlayer p=player(s);if(p==null)return 0;
   msg(p,"Difficulty: "+new String[]{"Easy","Normal","Hard"}[difficulty]+", duel: "+(active==null?"none":active.mode)+", record: "+wins+" wins / "+losses+" losses");return 1;
 }
 static int setDifficulty(CommandSourceStack s,int i){
   ServerPlayer p=player(s); if(p==null)return 0;
   difficulty=i;if(active!=null)attributes(active.bot);
   msg(p,"Difficulty changed to "+new String[]{"Easy","Normal","Hard"}[i]);return 1;
 }
 static void equip(Husk b,EquipmentSlot slot,Item i){b.setItemSlot(slot,new ItemStack(i));}
 static void gear(Husk b,Mode m){
   Item helm=Items.NETHERITE_HELMET, chest=Items.NETHERITE_CHESTPLATE;
   Item legs=Items.NETHERITE_LEGGINGS,boots=Items.NETHERITE_BOOTS;
   Item main=Items.NETHERITE_SWORD,off=Items.AIR;
   switch(m){
     case SWORD -> {helm=Items.DIAMOND_HELMET;chest=Items.DIAMOND_CHESTPLATE;legs=Items.DIAMOND_LEGGINGS;boots=Items.DIAMOND_BOOTS;main=Items.DIAMOND_SWORD;}
     case MACE -> main=Items.MACE;
     case SMP -> off=Items.TOTEM_OF_UNDYING;
     case ELYTRA_MACE -> {chest=Items.ELYTRA;main=Items.MACE;}
     case SPEAR_MACE -> main=Items.NETHERITE_SPEAR;
   }
   equip(b,EquipmentSlot.HEAD,helm);equip(b,EquipmentSlot.CHEST,chest);
   equip(b,EquipmentSlot.LEGS,legs);equip(b,EquipmentSlot.FEET,boots);
   equip(b,EquipmentSlot.MAINHAND,main);equip(b,EquipmentSlot.OFFHAND,off);
 }
 static void attr(Husk b,net.minecraft.core.Holder<Attribute> a,double val){
   AttributeInstance x=b.getAttribute(a);if(x!=null)x.setBaseValue(val);
 }
 static void attributes(Husk b){
   attr(b,Attributes.MAX_HEALTH,HP[difficulty]);attr(b,Attributes.ATTACK_DAMAGE,DAMAGE[difficulty]);
   attr(b,Attributes.MOVEMENT_SPEED,SPEED[difficulty]);
   attr(b,Attributes.FOLLOW_RANGE,32);
   b.setHealth((float)HP[difficulty]);
   for(EquipmentSlot e:EquipmentSlot.values())b.setDropChance(e,0);
 }
 static int start(CommandSourceStack src,Mode mode){
   ServerPlayer p=player(src);if(p==null)return 0;
   ServerLevel w=src.getLevel();
   if(w.getDifficulty()==Difficulty.PEACEFUL){msg(p,"Change world difficulty from Peaceful.");return 0;}
   stopInternal();
   Husk b=EntityType.HUSK.create(w,EntitySpawnReason.COMMAND);
   if(b==null){msg(p,"Could not spawn bot.");return 0;}
   Vec3 dir=p.getLookAngle();dir=new Vec3(dir.x,0,dir.z);
   if(dir.lengthSqr()<0.01)dir=new Vec3(0,0,1);
   Vec3 pos=p.position().add(dir.normalize().scale(5));
   boolean found=false;
   for(int y=0;y<3;y++){
     BlockPos feet=BlockPos.containing(pos.x,pos.y+y,pos.z);
     if(w.isEmptyBlock(feet)&&w.isEmptyBlock(feet.above())){pos=pos.add(0,y,0);found=true;break;}
   }
   if(!found){msg(p,"Try an open area.");return 0;}
   b.setPos(pos.x,pos.y,pos.z); b.setYRot(p.getYRot()+180);
   b.setCustomName(Component.literal(mode.name().replace('_',' ')+" Bot"));
   b.setCustomNameVisible(true);b.setPersistenceRequired();b.setSilent(true);
   b.setCanPickUpLoot(false);b.addTag("singleplayer_pvp_bot");
   gear(b,mode);attributes(b);
   if(!w.addFreshEntity(b)){msg(p,"Could not place bot.");return 0;}
   b.setTarget(p);active=new Fight(src.getServer(),w,p,b,mode);
   lastMode=mode;msg(p,mode+" started! Use /pvpbot kit to get gear.");return 1;
 }
 static void stopInternal(){
   if(active!=null){if(!active.bot.isRemoved())active.bot.discard();active=null;}
 }
 static int stop(CommandSourceStack s){ServerPlayer p=player(s);if(p==null)return 0;stopInternal();msg(p,"Bot removed.");return 1;}
 static int reset(CommandSourceStack s){ServerPlayer p=player(s);if(p==null)return 0;if(lastMode==null){msg(p,"Start a duel first.");return 0;}return start(s,lastMode);}
 static int kit(CommandSourceStack s){
   ServerPlayer p=player(s);if(p==null)return 0;
   Mode m=active==null?lastMode:active.mode;
   if(m==null){msg(p,"Start a duel first.");return 0;}
   String items=switch(m){
     case SWORD -> "diamond_sword diamond_helmet diamond_chestplate diamond_leggings diamond_boots golden_apple:8";
     case MACE -> "mace netherite_helmet netherite_chestplate netherite_leggings netherite_boots wind_charge:32 golden_apple:16";
     case SMP -> "netherite_sword netherite_helmet netherite_chestplate netherite_leggings netherite_boots totem_of_undying:2 shield golden_apple:16 ender_pearl:8";
     case ELYTRA_MACE -> "mace elytra netherite_helmet netherite_leggings netherite_boots firework_rocket:32 wind_charge:32 totem_of_undying";
     case SPEAR_MACE -> "netherite_spear mace netherite_helmet netherite_chestplate netherite_leggings netherite_boots wind_charge:16 golden_apple:16";
   };
   CommandSourceStack console=s.getServer().createCommandSourceStack().withLevel(s.getLevel()).withSuppressedOutput();
   for(String x:items.split(" ")){
      String[] parts=x.split(":");
      s.getServer().getCommands().performPrefixedCommand(console,"give "+p.getScoreboardName()+" minecraft:"+parts[0]+(parts.length==2?" "+parts[1]:""));
   }
   msg(p,"Kit added to inventory.");return 1;
 }
 static boolean ability(Fight f){return f.time-f.lastAbility>COOLDOWN[difficulty];}
 static void tick(MinecraftServer s){
   Fight f=active;if(f==null||f.server!=s)return;
   ServerPlayer p=s.getPlayerList().getPlayer(f.owner);
   if(p==null||p.level()!=f.world){stopInternal();return;}
   Husk b=f.bot;
   if(!b.isAlive()){wins++;msg(p,"Victory! Score "+wins+"-"+losses);active=null;return;}
   if(!p.isAlive()){losses++;msg(p,"Bot won! Score "+wins+"-"+losses);stopInternal();return;}
   f.time++;
   if(f.time%30==0&&b.getTarget()!=p)b.setTarget(p);
   double distance=b.distanceTo(p);
   if(f.mode==Mode.SWORD&&f.time%34==0&&distance<6&&distance>1.5){
      Vec3 delta=p.position().subtract(b.position());
      Vec3 strafe=new Vec3(-delta.z,0,delta.x).normalize().scale(f.time%68==0?0.22:-0.22);
      b.setDeltaMovement(b.getDeltaMovement().add(strafe));
   }
   if(f.mode==Mode.SMP){
      if(f.time%Math.max(85,COOLDOWN[difficulty]*2)==0&&b.getHealth()<HP[difficulty]*0.65){
        b.heal(5.0f);b.swing(net.minecraft.world.InteractionHand.OFF_HAND);
      }
      if(f.time%70==0&&distance>5&&distance<14){
        Vec3 delta=p.position().subtract(b.position()).normalize();
        b.setDeltaMovement(new Vec3(delta.x*0.36,0.24,delta.z*0.36));
      }
   }
   if(f.mode==Mode.SPEAR_MACE){
     boolean wantSpear=distance>=3.1;
     if(wantSpear!=f.spear&&f.time%12==0){
       equip(b,EquipmentSlot.MAINHAND,wantSpear?Items.NETHERITE_SPEAR:Items.MACE);f.spear=wantSpear;
     }
     if(f.spear&&distance>4.2&&distance<9&&ability(f)){
       Vec3 delta=p.position().subtract(b.position()).normalize();
       b.setDeltaMovement(new Vec3(delta.x*0.67,0.16,delta.z*0.67));f.lastAbility=f.time;
     }
   }
   if(f.mode==Mode.MACE||f.mode==Mode.ELYTRA_MACE||(f.mode==Mode.SPEAR_MACE&&!f.spear)){
     if(!f.diving&&ability(f)&&distance>1.7&&distance<7&&b.onGround()){
       Vec3 delta=p.position().subtract(b.position());
       Vec3 dir=new Vec3(delta.x,0,delta.z).normalize();
       if(f.mode==Mode.ELYTRA_MACE&&headroom(f.world,b,8)){
         b.teleportTo(b.getX(),b.getY()+7,b.getZ());
         b.setDeltaMovement(new Vec3(dir.x*0.55,-0.12,dir.z*0.55));
       }else b.setDeltaMovement(new Vec3(dir.x*0.26,1.0,dir.z*0.26));
       f.diving=true;f.launch=f.time;f.lastAbility=f.time;
     }
     if(f.diving&&b.onGround()&&f.time-f.launch>6){
       f.diving=false;
       if(b.distanceTo(p)<3.2){
         float damage=(float)new double[]{2,4,6}[difficulty];
         p.hurtServer(f.world,f.world.damageSources().mobAttack(b),damage);
       }
     }
     if(f.diving&&f.time-f.launch>70)f.diving=false;
   }
 }
 static boolean headroom(ServerLevel w,Husk b,int h){
   BlockPos pos=b.blockPosition();
   for(int i=1;i<=h;i++)if(!w.isEmptyBlock(pos.above(i)))return false;
   return true;
 }
}
