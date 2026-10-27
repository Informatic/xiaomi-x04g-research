package org.x04g.carouseldemo;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Version-pinned extension of the actual OEM pager; never replaces HOME or its APK. */
@SuppressWarnings({"deprecation","unchecked","rawtypes"})
public final class OemCarousel {
    public static final String ACTIVITY="com.google.assistant.surfaces.clock.launcher.AssistantLauncherActivity";
    private static final String REGISTRY="com.google.assistant.surfaces.clock.castshell.CastWindowFragmentRegistry";
    private static final String PAGER="com.google.assistant.surfaces.clock.home.TileViewPager";
    private static final String[] IDS={DemoSession.PAGE_ID};
    private static final int TAG=0x7f0a02e5;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static Session current;
    public enum State { NOT_INSTALLED, INSTALLED, REMOVED, CLEANUP_INCOMPLETE }
    private static State state=State.NOT_INSTALLED;
    public static String state(){return state.name();}
    private OemCarousel(){}
    public static void install(Activity activity){Bootstrap.attach(activity);}
    static void installVerified(Activity a)throws Exception{
        require(Looper.myLooper()==Looper.getMainLooper(),"main_thread_required");
        require(Bootstrap.isStockActivity(a),"activity_rejected");
        if(current!=null){require(current.activity==a,"other_activity_active");if(current.active&&current.fault==null&&bindingsCurrent(current))return;cleanup(current);require(current.cleaned,"previous_cleanup_incomplete");current=null;}
        Session s=discover(a);current=s;
        try{
            s.observerField.set(s.adapter,s.wrapper);
            for(int i=0;i<IDS.length;i++){s.fragments.put(IDS[i],s.pages[i]);s.ids.add(s.ownedIds[i]);}
            s.registered=true;s.active=true;s.rebuild.invoke(s.adapter);
            require(s.fault==null,"rebuild_fault");verify(s);
            s.pager.getViewTreeObserver().addOnScrollChangedListener(s.scroll);
            s.pager.getViewTreeObserver().addOnGlobalLayoutListener(s.layout);
            s.pager.getViewTreeObserver().addOnWindowFocusChangeListener(s.focus);
            s.homeView.addOnAttachStateChangeListener(s.attachment);
            s.listeners=true;s.resumed=true;visible(s);state=State.INSTALLED;
        }catch(Throwable e){cleanup(s);if(s.cleaned)current=null;throw e instanceof Exception?(Exception)e:new Exception("install_failure");}
    }
    static boolean installedFor(Activity a){return current!=null&&current.activity==a&&current.active&&current.fault==null&&bindingsCurrent(current);}
    static void resume(Activity a,boolean value){if(current!=null&&current.activity==a){current.resumed=value;visible(current);}}
    static void onTrimMemory(int level){if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(()->onTrimMemory(level));return;}if(current!=null&&current.active)current.runtime.onTrimMemory(level);}
    public static void remove(Activity a){if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(()->remove(a));return;}if(current!=null&&current.activity!=a)return;Bootstrap.disable(a);teardown(a);}
    /** Lifecycle teardown is temporary: the next real resume can install again. */
    static void teardown(Activity a){if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(()->teardown(a));return;}if(current==null||current.activity!=a)return;Session s=current;cleanup(s);state=s.cleaned?State.REMOVED:State.CLEANUP_INCOMPLETE;if(s.cleaned)current=null;else Bootstrap.note(Bootstrap.State.CLEANUP_INCOMPLETE);}
    public static final class HomePage extends Fragment {
        private static final String PAGE_ID="org.x04g.carouseldemo.page_id";
        public HomePage(){}
        HomePage(String id){Bundle args=new Bundle();args.putString(PAGE_ID,id);setArguments(args);}
        @Override public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle state){Session s=current;Bundle args=getArguments();String id=args==null?null:args.getString(PAGE_ID);int n=DemoSession.PAGE_ID.equals(id)?0:-1;if(n<0||s==null||!s.active||s.activity!=getActivity()||s.pages[n]!=this)return new View(inflater.getContext());return s.runtime.view();}
        @Override public void onDestroyView(){Session s=current;if(s!=null&&s.activity==getActivity()){if(s.pages[0]==this)s.runtime.pageDestroyed(DemoSession.PAGE_ID);}super.onDestroyView();}
    }
    private static final class Session {
        final Activity activity;final Object home,adapter,registry;final View pager,homeView;final FragmentManager manager;
        final Map fragments,containers;final List ids,models;final String[] ownedIds={new String(IDS[0])};final HomePage[] pages={new HomePage(IDS[0])};
        final Field observerField,type,data;final Method rebuild;final DataSetObserver original,wrapper;final DemoSession runtime;
        List previous;boolean registered,active,listeners,resumed,cleaned;String fault;
        final ViewTreeObserver.OnScrollChangedListener scroll=()->changed();
        final ViewTreeObserver.OnGlobalLayoutListener layout=()->changed();
        final ViewTreeObserver.OnWindowFocusChangeListener focus=hasFocus->changed();
        final View.OnAttachStateChangeListener attachment=new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View view){changed();}public void onViewDetachedFromWindow(View view){if(active)Bootstrap.rebind(activity);}};
        private boolean scheduled;
        Session(Activity a,Object h,Object ad,Object reg,View p,Map fs,Map ns,List gs,List js,Field ob,Field type,Field data,Method rebuild)throws Exception{
            activity=a;home=h;adapter=ad;registry=reg;pager=p;homeView=(View)method(h.getClass(),"getView").invoke(h);fragments=fs;containers=ns;ids=gs;models=js;manager=a.getFragmentManager();observerField=ob;this.type=type;this.data=data;this.rebuild=rebuild;original=(DataSetObserver)ob.get(ad);require(original!=null,"observer_missing");previous=new ArrayList(js);runtime=new DemoSession(a);
            wrapper=new DataSetObserver(){@Override public void onChanged(){Object selected=null;try{selected=selectedOld(Session.this);if(active)order(Session.this);}catch(Exception e){fault="order_failed";}original.onChanged();try{if(selected!=null)restore(Session.this,selected);previous=new ArrayList(models);changed();}catch(Exception e){fault="selection_failed";runtime.selected("",false);}}@Override public void onInvalidated(){original.onInvalidated();runtime.selected("",false);}};
        }
        void changed(){if(!active||scheduled)return;scheduled=true;MAIN.postDelayed(()->{scheduled=false;if(active){try{visible(this);}catch(Exception e){fault="visibility_failed";runtime.selected("",false);}}},32);}
    }
    private static Session discover(Activity a)throws Exception{
        require(a!=null&&ACTIVITY.equals(a.getClass().getName())&&!a.isFinishing()&&!a.isDestroyed(),"activity_mismatch");ClassLoader cl=a.getClass().getClassLoader();Class<?> cat=Class.forName("cat",false,cl),zb=Class.forName("zb",false,cl),cbg=Class.forName("cbg",false,cl),regClass=Class.forName(REGISTRY,false,cl),pagerClass=Class.forName(PAGER,false,cl);
        List<Object> homes=new ArrayList<Object>();Set<Object> visited=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());walk(a.getFragmentManager(),cat,homes,visited,0);walk(method(a.getClass(),"getSupportFragmentManager").invoke(a),cat,homes,visited,0);require(homes.size()==1,"home_not_ready");Object h=homes.get(0);
        require(method(h.getClass(),"getActivity").invoke(h)==a,"foreign_home");View homeView=(View)method(h.getClass(),"getView").invoke(h);require(homeView!=null&&homeView.getWindowToken()!=null,"home_not_attached");require(field(cat,"B").getType()==zb&&field(cat,"y").getType()==pagerClass&&field(cat,"b").getType()==regClass,"oem_shape_changed");Object ad=field(cat,"B").get(h),reg=field(cat,"b").get(h);View p=(View)field(cat,"y").get(h);require(ad!=null&&reg!=null&&p!=null&&p.getWidth()==800&&p.getHeight()==480,"pager_not_ready");require(field(p.getClass(),"b").get(p)==ad&&field(zb,"f").get(ad)==reg&&field(a.getClass(),"q").get(a)==reg,"owner_mismatch");
        FragmentManager fm=a.getFragmentManager();require(field(regClass,"d").get(reg)==fm&&!fm.isDestroyed()&&!fm.isStateSaved(),"fragment_state_unavailable");Map fs=(Map)field(regClass,"f").get(reg),ns=(Map)field(regClass,"n").get(reg);List gs=(List)field(regClass,"g").get(reg),js=(List)field(zb,"j").get(ad);require(fs!=null&&ns!=null&&gs instanceof ArrayList&&js instanceof ArrayList,"collections_changed");for(String id:IDS){for(String name:new String[]{"e","f","n","i"})require(!((Map)field(regClass,name).get(reg)).containsKey(id),"id_collision");for(String name:new String[]{"g","h"})require(!((List)field(regClass,name).get(reg)).contains(id),"id_collision");require(!id.equals(field(regClass,"m").get(reg))&&fm.findFragmentByTag("cast-fragment-"+id)==null,"id_collision");}
        Field type=field(cbg,"a"),data=field(cbg,"b"),ob=field(zb,"b");require(type.getType()==int.class&&data.getType()==Object.class&&ob.getType()==DataSetObserver.class,"model_shape_changed");int clocks=0;for(Object model:js){require(model!=null&&model.getClass()==cbg,"model_class_changed");if(type.getInt(model)==0)clocks++;for(String id:IDS)require(!id.equals(data.get(model)),"model_id_collision");}require(clocks==1,"clock_not_unique");Object ordering=field(zb,"p").get(ad);require(ordering!=null&&ordering.getClass().getName().equals("der"),"ordering_changed");int blocks=0;for(Object value:(List)field(ordering.getClass(),"a").get(ordering))if(Integer.valueOf(8).equals(value))blocks++;require(blocks==1,"tile_block_unavailable");return new Session(a,h,ad,reg,p,fs,ns,gs,js,ob,type,data,method(zb,"e"));
    }
    private static void walk(Object manager,Class<?> cat,List<Object> out,Set<Object> seen,int depth)throws Exception{if(manager==null||!seen.add(manager))return;require(depth<=12&&seen.size()<=96,"fragment_tree_bound");List list;if(manager instanceof FragmentManager)list=((FragmentManager)manager).getFragments();else{require(manager.getClass().getName().equals("bi"),"support_manager_changed");Object store=field(manager.getClass(),"b").get(manager);require(store!=null&&store.getClass().getName().equals("bo"),"support_store_changed");list=(List)method(store.getClass(),"g").invoke(store);}if(list==null)return;require(list.size()<=64,"fragment_list_bound");for(Object f:list)if(f!=null){if(f.getClass()==cat&&!out.contains(f))out.add(f);walk(f instanceof Fragment?((Fragment)f).getChildFragmentManager():method(f.getClass(),"getChildFragmentManager").invoke(f),cat,out,seen,depth+1);}}
    private static int own(Session s,Object model)throws Exception{if(s.type.getInt(model)!=8)return-1;Object d=s.data.get(model);for(int i=0;i<IDS.length;i++)if(IDS[i].equals(d))return i;return-1;}
    private static void order(Session s)throws Exception {
        Object clock=null,page=null;int clocks=0;
        for(Object model:s.models){
            if(s.type.getInt(model)==0){clock=model;clocks++;}
            if(own(s,model)>=0){require(page==null,"duplicate_own");page=model;}
        }
        require(clocks==1&&page!=null,"clock_or_page_missing");
        require(s.fragments.get(IDS[0])==s.pages[0]&&containsIdentity(s.ids,s.ownedIds[0]),"ownership_lost");
        List<Object> ordered=CarouselOrder.afterClock(new ArrayList<Object>(s.models),clock,page);
        s.models.clear();s.models.addAll(ordered);verify(s);
    }
    private static void verify(Session s)throws Exception{int clock=-1;for(int i=0;i<s.models.size();i++)if(s.type.getInt(s.models.get(i))==0)clock=i;require(clock>=0&&clock+IDS.length<s.models.size(),"priority_failed");for(int n=0;n<IDS.length;n++)require(own(s,s.models.get(clock+1+n))==n,"priority_failed");require(s.observerField.get(s.adapter)==s.wrapper,"observer_ownership_lost");}
    private static Object selectedOld(Session s)throws Exception{return selectedIn(s.pager,s.previous,s.type.getDeclaringClass());}
    private static Object selectedIn(View pager,List fallback,Class<?> modelType)throws Exception{int index=field(pager.getClass(),"c").getInt(pager);for(Object page:(List)field(pager.getClass(),"k").get(pager)){if(field(page.getClass(),"b").getInt(page)==index){Object view=field(page.getClass(),"a").get(page);if(view instanceof View){Object model=((View)view).getTag(TAG);if(model!=null&&model.getClass()==modelType)return model;}}}return index>=0&&index<fallback.size()?fallback.get(index):null;}
    private static void restore(Session s,Object selected)throws Exception{restoreIn(s.pager,s.models,selected);}
    private static void restoreIn(View pager,List models,Object selected)throws Exception{int wanted=indexIdentity(models,selected);if(wanted>=0&&field(pager.getClass(),"c").getInt(pager)!=wanted)method(pager.getClass(),"i",int.class,boolean.class).invoke(pager,wanted,false);}
    /** Identity, not just Activity identity, determines whether a Session is reusable. */
    private static boolean bindingsCurrent(Session s){try{return !s.activity.isFinishing()&&!s.activity.isDestroyed()&&!s.manager.isDestroyed()&&s.homeView.isAttachedToWindow()&&s.pager.isAttachedToWindow()&&s.homeView.getWindowToken()!=null&&s.pager.getWindowToken()!=null&&method(s.home.getClass(),"getActivity").invoke(s.home)==s.activity&&method(s.home.getClass(),"getView").invoke(s.home)==s.homeView&&field(s.home.getClass(),"B").get(s.home)==s.adapter&&field(s.home.getClass(),"y").get(s.home)==s.pager&&field(s.home.getClass(),"b").get(s.home)==s.registry&&field(s.activity.getClass(),"q").get(s.activity)==s.registry&&field(s.pager.getClass(),"b").get(s.pager)==s.adapter&&field(s.adapter.getClass(),"f").get(s.adapter)==s.registry&&field(s.adapter.getClass(),"j").get(s.adapter)==s.models&&field(s.registry.getClass(),"d").get(s.registry)==s.manager&&field(s.registry.getClass(),"f").get(s.registry)==s.fragments&&field(s.registry.getClass(),"n").get(s.registry)==s.containers&&field(s.registry.getClass(),"g").get(s.registry)==s.ids&&s.observerField.get(s.adapter)==s.wrapper;}catch(Exception error){return false;}}
    /** A new OEM adapter may already have copied our IDs before the old View detached. */
    private static void refreshReplacement(Session s)throws Exception{if(s.activity.isFinishing()||s.activity.isDestroyed())return;List<Object> homes=new ArrayList<Object>();Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());walk(s.activity.getFragmentManager(),s.home.getClass(),homes,seen,0);walk(method(s.activity.getClass(),"getSupportFragmentManager").invoke(s.activity),s.home.getClass(),homes,seen,0);require(homes.size()<=1,"home_transition_pending");if(homes.isEmpty())return;Object h=homes.get(0);Object replacement=field(h.getClass(),"B").get(h);if(replacement==null||replacement==s.adapter)return;Object reg=field(h.getClass(),"b").get(h);if(reg!=s.registry)return;View pager=(View)field(h.getClass(),"y").get(h);require(method(h.getClass(),"getActivity").invoke(h)==s.activity&&replacement.getClass()==s.adapter.getClass()&&pager!=null&&pager.getClass()==s.pager.getClass()&&field(pager.getClass(),"b").get(pager)==replacement&&field(replacement.getClass(),"f").get(replacement)==reg&&field(s.activity.getClass(),"q").get(s.activity)==reg,"replacement_binding_changed");List models=(List)field(replacement.getClass(),"j").get(replacement);Object selected=selectedIn(pager,models,s.type.getDeclaringClass());if(selected!=null&&own(s,selected)>=0){selected=null;for(Object model:models)if(s.type.getInt(model)==0){selected=model;break;}}s.rebuild.invoke(replacement);for(Object model:models)require(own(s,model)<0,"replacement_own_models_remain");if(selected!=null)restoreIn(pager,models,selected);}
    /** Read-only overlay guard; the demo never replaces or extends the OEM idle timer. */
    private static boolean homeIsTop(Session s)throws Exception {
        Object manager=field(s.home.getClass(),"h").get(s.home);
        require(manager!=null&&"com.google.assistant.surfaces.clock.common.ViewManager".equals(manager.getClass().getName()),"view_manager_changed");
        Object value=field(manager.getClass(),"c").get(manager);
        require(value instanceof ViewGroup&&"com.google.assistant.surfaces.clock.common.GlanceableContainerHolder".equals(value.getClass().getName()),"holder_changed");
        ViewGroup holder=(ViewGroup)value;
        if(holder.getChildCount()!=1)return false;
        View base=holder.getChildAt(0);
        require(base!=null&&"bwa".equals(base.getClass().getName()),"base_changed");
        if(field(base.getClass(),"d").get(base)!=s.home)return false;
        View ancestor=s.homeView;int depth=0;
        while(ancestor!=base&&ancestor.getParent() instanceof View&&depth++<64)ancestor=(View)ancestor.getParent();
        return ancestor==base;
    }
    private static void visible(Session s){try{if(!s.active||s.fault!=null){s.runtime.selected("",false);return;}if(!bindingsCurrent(s)){s.runtime.selected("",false);Bootstrap.rebind(s.activity);return;}int index=field(s.pager.getClass(),"c").getInt(s.pager);String id="";if(index>=0&&index<s.models.size()){int n=own(s,s.models.get(index));if(n>=0)id=IDS[n];}s.runtime.selected(id,s.resumed&&s.activity.hasWindowFocus()&&s.pager.isShown()&&homeIsTop(s));}catch(Exception e){s.fault="visibility_failed";s.runtime.selected("",false);}}
    private static void cleanup(Session s){s.active=false;s.runtime.selected("",false);try{if(s.listeners){s.homeView.removeOnAttachStateChangeListener(s.attachment);ViewTreeObserver v=s.pager.getViewTreeObserver();if(v.isAlive()){v.removeOnScrollChangedListener(s.scroll);v.removeOnGlobalLayoutListener(s.layout);v.removeOnWindowFocusChangeListener(s.focus);}s.listeners=false;}Object observer=s.observerField.get(s.adapter);require(observer==s.wrapper||observer==s.original,"observer_ownership_lost");if(observer==s.wrapper)s.observerField.set(s.adapter,s.original);Object selected=selectedOld(s);if(selected!=null&&own(s,selected)>=0){selected=null;for(Object m:s.models)if(s.type.getInt(m)==0)selected=m;if(selected!=null)restore(s,selected);}s.manager.executePendingTransactions();for(int i=0;i<IDS.length;i++){if(s.fragments.get(IDS[i])==s.pages[i]){s.manager.beginTransaction().remove(s.pages[i]).commitNowAllowingStateLoss();s.fragments.remove(IDS[i]);s.containers.remove(IDS[i]);}removeIdentity(s.ids,s.ownedIds[i]);}s.rebuild.invoke(s.adapter);refreshReplacement(s);if(selected!=null)restore(s,selected);s.cleaned=true;}catch(Exception e){s.fault="cleanup_incomplete";}finally{s.runtime.close();}}
    private static int indexIdentity(List list,Object value){for(int i=0;i<list.size();i++)if(list.get(i)==value)return i;return -1;}
    private static boolean containsIdentity(List list,Object value){for(Object x:list)if(x==value)return true;return false;}
    private static void removeIdentity(List list,Object value){for(int i=list.size()-1;i>=0;i--)if(list.get(i)==value)list.remove(i);}
    private static Field field(Class<?> c,String name)throws Exception{for(Class<?> k=c;k!=null;k=k.getSuperclass())try{Field f=k.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new Exception("oem_field_missing");}
    private static Method method(Class<?> c,String name,Class<?>...types)throws Exception{for(Class<?> k=c;k!=null;k=k.getSuperclass())try{Method m=k.getDeclaredMethod(name,types);m.setAccessible(true);return m;}catch(NoSuchMethodException ignored){}throw new Exception("oem_method_missing");}
    private static void require(boolean value,String code){if(!value)throw new IllegalStateException(code);}
}
