package io.github.phiscript.root;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.assets.ChartLibrary;
import io.github.phiscript.assets.PhiraLibrary;
import io.github.phiscript.input.TouchService;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Two native pages with independent scroll positions and one shared transport state. */
public final class HomeScreen {
    private static final int INK=UiDesign.INK,MUTED=UiDesign.MUTED,ACCENT=UiDesign.ACCENT;
    private final Activity activity;
    private final LinearLayout root;
    private TextView badge,summary,phigrosCount,phiraCount,permission,mode;
    private Button primary;
    private Switch recording;
    private static final int PERFORMANCE=0,LIBRARY=1;
    private final ScrollView[] pages=new ScrollView[2];
    private final TextView[] tabs=new TextView[2];
    private int primaryState=-1,selectedPage=-1;
    private boolean motionPaused;
    private final Runnable ticker=new Runnable(){public void run(){
        if(RootPanel.alive(activity)&&root.isAttachedToWindow()&&root.hasWindowFocus()){
            update();
            root.postDelayed(this,700);
        }
    }};
    private final ViewTreeObserver.OnWindowFocusChangeListener focusChanged=this::scheduleRefresh;
    private HomeScreen(Activity activity,LinearLayout root){this.activity=activity;this.root=root;}
    private void scheduleRefresh(boolean focused){
        root.removeCallbacks(ticker);
        if(focused)root.post(ticker);
    }
    private void observeFocus(){
        root.getViewTreeObserver().addOnWindowFocusChangeListener(focusChanged);
        scheduleRefresh(root.hasWindowFocus());
    }
    public static void attach(Activity a){
        io.github.phiscript.i18n.UiText.attach(a);io.github.phiscript.GamePackages.init(a);
        LinearLayout root=find(a.findViewById(android.R.id.content));if(root==null||root.getTag() instanceof HomeScreen)return;
        HomeScreen home=new HomeScreen(a,root);root.setTag(home);home.unwrapRoot();home.build();
        io.github.phiscript.assets.ExternalCharts.attach(a);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){home.observeFocus();}
            public void onViewDetachedFromWindow(View v){
                ViewTreeObserver observer=root.getViewTreeObserver();
                if(observer.isAlive())observer.removeOnWindowFocusChangeListener(home.focusChanged);
                home.scheduleRefresh(false);
                UiMotion.cancelTree(root);
            }
        });
        if(root.isAttachedToWindow())home.observeFocus();
    }
    public static void refresh(Activity a){LinearLayout root=find(a.findViewById(android.R.id.content));if(root!=null&&root.getTag() instanceof HomeScreen)((HomeScreen)root.getTag()).update();}
    private void unwrapRoot(){
        if(!(root.getParent() instanceof ScrollView))return;
        ScrollView old=(ScrollView)root.getParent();if(!(old.getParent() instanceof ViewGroup))return;
        ViewGroup parent=(ViewGroup)old.getParent();int at=parent.indexOfChild(old);
        ViewGroup.LayoutParams layout=old.getLayoutParams();
        old.removeView(root);parent.removeView(old);parent.addView(root,at,layout);
    }
    private static LinearLayout find(View v){
        if(v instanceof LinearLayout&&((LinearLayout)v).getOrientation()==LinearLayout.VERTICAL)return(LinearLayout)v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){LinearLayout r=find(g.getChildAt(i));if(r!=null)return r;}}
        return null;
    }
    private String w(String zh,String en){return UiDesign.words(zh,en);}
    private LinearLayout page(FrameLayout viewport,int index,String title){
        ScrollView scroll=new ScrollView(activity);pages[index]=scroll;
        scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setTag(index==PERFORMANCE?"data-performance-page":"data-library-page");
        viewport.addView(scroll,new FrameLayout.LayoutParams(-1,-1));scroll.setVisibility(View.GONE);
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        // Center a readable column on wide windows without clipping compact or split-screen layouts.
        int width=activity.getResources().getConfiguration().screenWidthDp;
        int side=dp(Math.max(width<360?14:20,(width-640)/2));
        content.setPadding(side,dp(20),side,dp(24));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        LinearLayout brand=new LinearLayout(activity);brand.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(brand,new LinearLayout.LayoutParams(-1,-2));
        android.widget.ImageView logo=new android.widget.ImageView(activity);
        logo.setImageResource(activity.getApplicationInfo().icon);logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        brand.addView(logo,new LinearLayout.LayoutParams(dp(28),dp(28)));
        TextView name=UiDesign.text(activity,"Phigros Script",15,INK,true);
        LinearLayout.LayoutParams flexible=new LinearLayout.LayoutParams(0,-2,1);flexible.setMarginStart(dp(8));brand.addView(name,flexible);
        try{
            String version=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;
            if(!TextUtils.isEmpty(version))brand.addView(UiDesign.text(activity,version,12,MUTED,false),new LinearLayout.LayoutParams(-2,-2));
        }catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        TextView heading=text(content,title,32,INK,true);heading.setPadding(0,dp(20),0,dp(2));
        if(android.os.Build.VERSION.SDK_INT>=28){heading.setAccessibilityHeading(true);scroll.setAccessibilityPaneTitle(title);}
        return content;
    }
    private void navigation(GlassStage stage){
        FrameLayout controls=new FrameLayout(activity);
        stage.addView(controls,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout bar=new LinearLayout(activity);bar.setGravity(Gravity.CENTER);
        bar.setBackground(GlassSurface.dock(activity));bar.setElevation(dp(3));
        bar.setPadding(dp(6),dp(4),dp(6),dp(4));
        // Keep the two tabs within thumb reach in a broad landscape or tablet window.
        int width=activity.getResources().getConfiguration().screenWidthDp;
        int margin=dp(Math.max(12,(width-440)/2));
        FrameLayout.LayoutParams dock=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        dock.setMargins(margin,dp(4),margin,dp(6));controls.addView(bar,dock);
        navItem(bar,PERFORMANCE,w("演奏","Play"),UiDesign.PLAY);
        navItem(bar,LIBRARY,w("谱库","Library"),UiDesign.LIBRARY);
        stage.setDock(bar);
        for(ScrollView page:pages)stage.followScroll(page);
        Runnable updateScrollPadding=()->{
            int dockHeight=dock.bottomMargin+bar.getHeight()+dp(10);
            for(ScrollView page:pages)HomeInsets.padPage(page,controls,dockHeight);
        };
        bar.addOnLayoutChangeListener((v,l,t,r,b,oldL,oldT,oldR,oldB)->{
            updateScrollPadding.run();
            stage.invalidate();
        });
        HomeInsets.install(activity.getWindow(),root,controls,updateScrollPadding);
    }
    private void navItem(LinearLayout bar,int index,String label,int kind){
        Button tab=UiDesign.button(activity,label,false,()->selectPage(index));tabs[index]=tab;
        tab.setTextSize(13);tab.setMinWidth(0);tab.setMinimumWidth(0);
        tab.setGravity(Gravity.CENTER);tab.setMinHeight(dp(52));tab.setPadding(dp(4),dp(4),dp(4),dp(4));
        tab.setCompoundDrawablesWithIntrinsicBounds(null,UiDesign.mark(activity,kind),null,null);
        tab.setCompoundDrawablePadding(dp(2));tab.setFocusable(true);tab.setTag(index==PERFORMANCE?"data-tab-performance":"data-tab-library");
        LinearLayout.LayoutParams slot=new LinearLayout.LayoutParams(0,-2,1);slot.setMarginStart(dp(4));slot.setMarginEnd(dp(4));
        bar.addView(tab,slot);
    }
    private void selectPage(int index){
        if(index==selectedPage)return;
        UiMotion.switchPage(selectedPage>=0?pages[selectedPage]:null,pages[index]);
        selectedPage=index;
        for(int i=0;i<tabs.length;i++){
            boolean active=i==index;TextView tab=tabs[i];tab.setSelected(active);
            tab.setTextColor(active?ACCENT:MUTED);tab.setCompoundDrawableTintList(ColorStateList.valueOf(active?ACCENT:MUTED));
            tab.setBackground(UiDesign.ripple(activity,active?UiDesign.MINT:android.graphics.Color.TRANSPARENT,20));
        }
        activity.getPreferences(0).edit().putInt("home_page",index).apply();
    }
    private void build(){
        TextView oldStatus=field("statusView"),oldAccess=field("accessibilityView"),oldLog=field("logView");
        activity.setTheme(android.R.style.Theme_Material_Light_NoActionBar);
        root.removeAllViews();root.setBackground(null);root.setPadding(0,0,0,0);root.setGravity(Gravity.TOP);
        GlassStage stage=new GlassStage(activity);
        root.addView(stage,new LinearLayout.LayoutParams(-1,-1));
        FrameLayout viewport=stage.pages;
        GlassWindows.home(activity.getWindow(),viewport);
        LinearLayout performance=page(viewport,PERFORMANCE,w("演奏台","Performance"));
        LinearLayout library=page(viewport,LIBRARY,w("谱库","Library"));
        LinearLayout transport=card(performance);transport.setBackground(new GlassSurface(activity,20,true));
        badge=text(transport,"",13,ACCENT,true);badge.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        mode=text(transport,"",22,INK,true);mode.setPadding(0,dp(12),dp(8),dp(10));mode.setMinHeight(dp(52));mode.setGravity(Gravity.CENTER_VERTICAL);mode.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
        mode.setBackground(UiDesign.ripple(activity,android.graphics.Color.TRANSPARENT,10));mode.setFocusable(true);mode.setOnClickListener(v->PlayEntry.choose(activity));UiMotion.bindPress(mode);
        summary=text(transport,"",13,MUTED,false);summary.setMinHeight(dp(42));
        primary=action(transport,w("开始打谱","Start performance"),true,()->{
            if(RootPhiraImport.isBusy()||PhiraImportControl.isBusy()){RootPhiraImport.cancel();PhiraImportControl.cancel();}
            else if(RuntimeState.INSTANCE.getRunning().get()||ManualChartControl.isOpen())AutoEntry.stop(activity);else PlayEntry.start(activity,false);update();
        });primary.setTag("data-primary-transport");
        text(transport,w("保持游戏横屏 · 音量减键可停止","Keep landscape · Volume down stops"),12,MUTED,false).setPadding(0,dp(12),0,0);
        LinearLayout phigros=card(library);label(phigros,"Phigros");
        phigrosCount=text(phigros,"",13,MUTED,false);
        triad(phigros,"Phigros",new String[]{w("下载","Get charts"),w("导入","Import"),w("删除 / 清理","Delete")},new Runnable[]{this::downloadCharts,this::importCharts,()->PhigrosLibraryManagement.open(activity)},new String[]{"data-phigros-download","data-import","data-phigros-delete"});
        LinearLayout phira=card(library);label(phira,"Phira / Pro");
        phiraCount=text(phira,"",13,MUTED,false);
        triad(phira,"Phira / Pro",new String[]{w("在线谱库","Online"),w("导入","Import"),w("删除","Delete")},new Runnable[]{()->OnlinePhiraLibraryUi.open(activity),()->{if(!RootPanel.blocked(activity))invoke(activity,"choosePhira");},()->PhiraLibraryManagement.open(activity)},new String[]{"data-phira-online","data-phira-import","data-phira-delete"});
        LinearLayout prepare=UiDesign.row(activity,UiDesign.SETTINGS,w("预计算触点","Prepare touch points"),w("调整设置后，重新准备已缓存谱面","Refresh prepared charts after changing settings"),()->PreparedChartsUi.open(activity));prepare.setTag("data-prepare-charts");library.addView(prepare,params(-1,-2,12));
        LinearLayout controls=card(performance);label(controls,w("权限与设置","Access & settings"));permission=text(controls,"",12,MUTED,false);permission.setPadding(0,dp(8),0,dp(4));
        controls.addView(UiDesign.row(activity,UiDesign.SHIELD,w("权限与触控","Permissions & input"),w("无障碍、悬浮窗与 Root","Accessibility, overlay and Root"),this::permissions),new LinearLayout.LayoutParams(-1,-2));
        controls.addView(UiDesign.row(activity,UiDesign.SETTINGS,w("设置与工具","Settings & tools"),w("校准、识别、手势与语言","Calibration, recognition, gestures and language"),this::settings),new LinearLayout.LayoutParams(-1,-2));
        controls.addView(UiDesign.row(activity,UiDesign.PLAY,w("启动 Phira Pro","Open Phira Pro"),null,()->GameLaunch.phira(activity,true,true)),new LinearLayout.LayoutParams(-1,-2));
        LinearLayout video=card(library);label(video,w("录屏","Recording"));
        recording=new Switch(activity);recording.setText(w("打谱时自动录屏","Record performances"));recording.setTextSize(15);recording.setTextColor(INK);recording.setMinHeight(dp(56));recording.setPadding(0,dp(8),0,dp(8));
        int[][] switchStates={{-android.R.attr.state_enabled},{android.R.attr.state_checked},{}};
        recording.setThumbTintList(new ColorStateList(switchStates,new int[]{0xff97a79e,ACCENT,MUTED}));
        recording.setTrackTintList(new ColorStateList(switchStates,new int[]{UiDesign.LINE,0xff9ed1b9,0xffbdc9c2}));
        recording.setChecked(new AppSettings(activity).flag("recording_on_play",true));video.addView(recording,new LinearLayout.LayoutParams(-1,-2));
        recording.setOnCheckedChangeListener((v,on)->{if(!RuntimeState.INSTANCE.getRunning().get()&&!RootPanel.isBusy())new AppSettings(activity).saveFlag("recording_on_play",on);});
        text(video,w("演奏结束保存无声 MP4","Silent MP4 is saved when the performance ends"),12,MUTED,false);
        video.addView(UiDesign.row(activity,UiDesign.VIDEO,w("分辨率与帧率","Resolution & frame rate"),w("按设备选择录屏档位","Choose a profile for your device"),()->io.github.phiscript.record.RecordingSettings.show(activity)),params(-1,-2,8));
        video.addView(UiDesign.row(activity,UiDesign.HISTORY,w("最近录屏","Recent recordings"),w("查看或分享已保存的视频","View or share saved videos"),()->{try{activity.startActivity(io.github.phiscript.record.RecordingFiles.view(activity));}catch(Exception e){RootPanel.message(activity,tr("录屏位置"),tr("Android 10+：Movies/PhigrosScript；Android 8/9：应用录屏目录。\n\n")+e.getMessage());}}),new LinearLayout.LayoutParams(-1,-2));
        LinearLayout diagnostics=card(performance),details=new LinearLayout(activity);details.setOrientation(1);details.setVisibility(View.GONE);
        TextView expand=text(diagnostics,w("运行记录  ＋","Activity log  +"),14,MUTED,true);expand.setMinHeight(dp(48));expand.setGravity(Gravity.CENTER_VERTICAL);expand.setFocusable(true);expand.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
        expand.setContentDescription(w("展开运行记录","Expand activity log"));expand.setBackground(UiDesign.ripple(activity,android.graphics.Color.TRANSPARENT,10));
        expand.setOnClickListener(v->{boolean open=!UiMotion.expanded(details);UiMotion.toggle(details,open);expand.setText(open?w("运行记录  −","Activity log  −"):w("运行记录  ＋","Activity log  +"));expand.setContentDescription(open?w("收起运行记录","Collapse activity log"):w("展开运行记录","Expand activity log"));});UiMotion.bindPress(expand);diagnostics.addView(details,new LinearLayout.LayoutParams(-1,-2));
        for(TextView v:new TextView[]{oldStatus,oldAccess,oldLog})if(v!=null){if(v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);v.setTextColor(MUTED);v.setTextSize(12);v.setPadding(0,dp(8),0,dp(8));details.addView(v,new LinearLayout.LayoutParams(-1,-2));}
        navigation(stage);
        selectPage(activity.getPreferences(0).getInt("home_page",PERFORMANCE)==LIBRARY?LIBRARY:PERFORMANCE);
        update();
    }
    private void triad(LinearLayout parent,String platform,String[] labels,Runnable[] actions,String[] tags){
        ChartActions row=new ChartActions(activity);parent.addView(row,params(-1,-2,10));
        for(int i=0;i<labels.length;i++){
            Button b=UiDesign.button(activity,labels[i],false,actions[i]);b.setTag(tags[i]);
            b.setMinWidth(0);b.setMinimumWidth(0);b.setTextSize(14);b.setPadding(dp(8),dp(12),dp(8),dp(12));
            b.setContentDescription(platform+", "+labels[i]);row.addView(b);
        }
    }
    /** Re-evaluate against the measured label width on rotation, split screen and font scaling. */
    private static final class ChartActions extends LinearLayout {
        private final int gap;
        private int arranged=-1;
        ChartActions(Activity activity){super(activity);gap=UiDesign.dp(activity,6);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int widest=0,count=getChildCount();
            for(int i=0;i<count;i++){
                Button b=(Button)getChildAt(i);
                int width=(int)Math.ceil(b.getPaint().measureText(b.getText().toString()));
                widest=Math.max(widest,width+b.getCompoundPaddingLeft()+b.getCompoundPaddingRight());
            }
            int available=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight();
            int layout=MeasureSpec.getMode(widthSpec)!=MeasureSpec.UNSPECIFIED
                &&widest*count+gap*(count-1)>available?VERTICAL:HORIZONTAL;
            if(layout!=arranged){
                arranged=layout;boolean stack=layout==VERTICAL;setOrientation(layout);
                for(int i=0;i<count;i++){
                    LayoutParams p=(LayoutParams)getChildAt(i).getLayoutParams();
                    p.width=stack?LayoutParams.MATCH_PARENT:0;p.height=LayoutParams.WRAP_CONTENT;p.weight=stack?0:1;
                    p.setMarginStart(i>0&&!stack?gap:0);p.topMargin=i>0&&stack?gap:0;
                    getChildAt(i).setLayoutParams(p);
                }
            }
            super.onMeasure(widthSpec,heightSpec);
        }
    }
    private void update(){
        boolean running=RuntimeState.INSTANCE.getRunning().get()||ManualChartControl.isOpen(),busy=RootPanel.isBusy();
        boolean touch=RuntimeState.INSTANCE.getActiveTouchStop().get()!=null;
        if((running||touch)&&!motionPaused)UiMotion.cancelTree(root);motionPaused=running||touch;
        AppSettings prefs=new AppSettings(activity);
        String currentMode=io.github.phiscript.i18n.UiText.text(ModeRouting.label(prefs));
        UiMotion.state(mode,currentMode+"  ⌄");
        String modeDescription=w("切换演奏模式：","Change performance mode: ")+currentMode;
        if(!TextUtils.equals(mode.getContentDescription(),modeDescription))mode.setContentDescription(modeDescription);
        boolean importing=RootPhiraImport.isBusy()||PhiraImportControl.isBusy();
        String lastError=!running&&!busy?prefs.text("phigros_last_error",""):"";
        String status=!lastError.isEmpty()?"上次自动检测已停止":RootPanel.isRootPending()?tr("正在等待 ROOT 授权"):running?(touch?tr("正在演奏"):tr("自动检测已开启")):busy?tr("正在处理谱库"):tr("待机");
        UiMotion.state(badge,io.github.phiscript.i18n.UiText.text(status));
        String log=running||importing?RuntimeState.INSTANCE.logText():null;
        String value=log==null?"":log.trim();
        if(!value.isEmpty()){
            int line=value.lastIndexOf('\n');if(line>=0)value=value.substring(line+1);UiMotion.state(summary,io.github.phiscript.i18n.UiText.text(value.length()>120?value.substring(0,120)+"…":value));
        }else if(!lastError.isEmpty()){
            UiMotion.state(summary,io.github.phiscript.i18n.UiText.text(lastError));
        }else UiMotion.state(summary,io.github.phiscript.i18n.UiText.text(RootPanel.isRootPending()?tr("授权结束后会自动更新状态，返回游戏后继续确认曲目。"):
            ModeRouting.kind(prefs)==ModeRouting.Kind.PHIGROS_AUTO?tr("进入歌曲画面后自动暂停，再识别谱面并点击中央重试。"):tr("选择与游戏一致的模式，导入谱面后即可开始。")));
        boolean cancelling=RootPhiraImport.isCancelling()||PhiraImportControl.isCancelling();
        UiMotion.state(primary,io.github.phiscript.i18n.UiText.text(cancelling?tr("正在取消谱面导入…"):importing?tr("取消谱面导入"):running?tr("停止打谱"):RootPanel.isRootPending()?tr("等待 Root 授权…"):busy?tr("正在处理…"):tr("开始打谱")));
        boolean transportEnabled=!cancelling&&(importing||running||!busy);primary.setEnabled(transportEnabled);
        int nextState=!transportEnabled?2:running?1:0;
        if(primaryState!=nextState){
            primaryState=nextState;
            primary.setBackground(UiDesign.ripple(activity,!transportEnabled?UiDesign.RAISED:running?UiDesign.DANGER:ACCENT,14));
            primary.setTextColor(!transportEnabled?MUTED:UiDesign.ON_ACCENT);
        }
        primary.setContentDescription(primary.getText());
        ChartLibrary phi=RuntimeState.INSTANCE.getLibrary();PhiraLibrary phira=RuntimeState.INSTANCE.getPhiraLibrary();
        UiMotion.state(phigrosCount,phi==null?tr("未导入"):phi.getLocations().size()+tr(" 个谱面难度"));
        UiMotion.state(phiraCount,phira==null?tr("未导入"):phira.getEntries().size()+tr(" 个谱面"));
        UiMotion.state(permission,io.github.phiscript.i18n.UiText.text((InputBackend.rootEnabled?tr("Root 触控"):tr("无障碍触控"))+"  ·  "+(TouchService.Companion.getCurrent()!=null?tr("无障碍已连接"):tr("无障碍未连接"))+"\n"+(Settings.canDrawOverlays(activity)?tr("悬浮窗已允许"):tr("悬浮窗待授权"))+tr("  ·  截屏权限在开始时申请")));
        recording.setEnabled(!running&&!busy);boolean enabled=prefs.flag("recording_on_play",true);if(recording.isChecked()!=enabled)recording.setChecked(enabled);
        UiMotion.state(recording,io.github.phiscript.i18n.UiText.text(tr("打谱时录屏（")+io.github.phiscript.record.RecordingPreferences.load(activity).label()+"）"));
    }
    private void downloadCharts(){
        if(RootPanel.blocked(activity))return;
        UiDesign.show(UiDesign.dialog(activity,w("下载 Phigros 谱面","Get Phigros charts")).setItems(new String[]{
            w("Google 云盘 · 识别后自动下载","Google Drive · Download on song recognition"),
            w("Swordalt · 原谱与离线包","Swordalt · Original charts and offline ZIPs"),
            w("从链接导入谱面","Import from a link")
        },(d,i)->{
            if(i==0)io.github.phiscript.assets.ExternalCharts.useDrive(activity);
            else if(i==1)io.github.phiscript.assets.SwordaltLibraryUi.open(activity);
            else io.github.phiscript.assets.ExternalCharts.network(activity);
        }).setNegativeButton(io.github.phiscript.i18n.UiText.text("取消"),null).create());
    }
    private void importCharts(){
        if(RootPanel.blocked(activity))return;
        UiDesign.show(UiDesign.dialog(activity,w("导入 Phigros 谱库","Import Phigros charts")).setItems(new String[]{w("导入本地 PEZ / ZIP / JSON","Import local PEZ / ZIP / JSON"),w("使用已导入的 PEZ / ZIP","Use imported PEZ / ZIP"),w("导入已安装游戏 APK","Import installed game APK"),w("选择 APK 文件","Choose APK files")},(d,i)->{
            if(i==0)io.github.phiscript.assets.ExternalCharts.choose(activity);else if(i==1)io.github.phiscript.assets.ExternalCharts.useLocal(activity);else if(i==2)importInstalledCharts();else invoke(activity,"chooseApks");
        }).setNegativeButton(io.github.phiscript.i18n.UiText.text("取消"),null).create());
    }

    private void importInstalledCharts(){
        if(RootPanel.blocked(activity))return;
        if(InputBackend.rootEnabled)RootPanel.scan(activity);
        else invoke(activity,"scan",new Class<?>[]{kotlin.jvm.functions.Function0.class},new Object[]{null});
    }

    private void permissions(){
        UiDesign.show(UiDesign.dialog(activity, "").setTitle(io.github.phiscript.i18n.UiText.text("权限与触控")).setItems(io.github.phiscript.i18n.UiText.text(new String[]{tr("选择触控方式：Root / 无障碍"),tr("启用无障碍服务"),tr("允许悬浮窗"),tr("Shizuku 辅助与诊断")}),(d,i)->{
            if(i==0){
                if(RootPanel.blocked(activity))return;
                UiDesign.show(UiDesign.dialog(activity, "").setTitle(io.github.phiscript.i18n.UiText.text("触控方式")).setSingleChoiceItems(io.github.phiscript.i18n.UiText.text(new String[]{tr("Root（需授权）"),tr("无障碍")}),InputBackend.rootEnabled?0:1,(dialog,index)->{
                    dialog.dismiss();if(index==0){if(!InputBackend.rootEnabled)RootPanel.requestRoot(activity);}else RootPanel.useAccessibility(activity);
                }).setNegativeButton(io.github.phiscript.i18n.UiText.text("取消"),null).create());
            }else if(i==1)activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            else if(i==2)activity.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+activity.getPackageName())));
            else shizuku();
        }).setNegativeButton(io.github.phiscript.i18n.UiText.text("关闭"),null).create());
    }
    private void settings(){
        UiDesign.show(UiDesign.dialog(activity, "").setTitle(io.github.phiscript.i18n.UiText.text("设置与工具")).setItems(io.github.phiscript.i18n.UiText.text(new String[]{tr("Phigros / Phira · 重试校准"),tr("识别区域与设备设置"),tr("修正曲名识别"),tr("Phira · 自动识别 / 选择谱面"),tr("只识别预览"),tr("RPE 兼容说明"),tr("连键手势与边缘避让"),tr("语言 / Language"),tr("检查更新"),tr("噪域与随机点击")}),(d,i)->{
            switch(i){
                case 0:AutoEntry.settings(activity);break;
                case 1:RecognitionSettings.open(activity);break;
                case 2:invoke(activity,"editAlias");break;
                case 3:if(!RootPanel.blocked(activity)){ModeRouting.select(new AppSettings(activity),1);invoke(activity,"choosePhiraChart");}break;
                case 4:PlayEntry.start(activity,true);break;
                case 5:RootPanel.message(activity,tr("RPE 兼容说明"),tr("支持 JSON / ZIP / PEZ、长键多押、父子线、变 BPM、上下侧及已适配的多层事件。这里只计算触控，不渲染视频或特效；自定义脚本、带音符的 UI 绑定线及新版扩展仍有限制。不会保证所有谱面兼容或全连。"));break;
                case 6:GestureSettings.open(activity);break;
                case 7:io.github.phiscript.i18n.LanguageSettings.open(activity);break;
                case 8:io.github.phiscript.update.UpdateChecker.check(activity);break;
                case 9:TapSettings.open(activity);break;
            }
        }).setNegativeButton(io.github.phiscript.i18n.UiText.text("关闭"),null).create());
    }
    private void shizuku(){
        UiDesign.show(UiDesign.dialog(activity, "").setTitle(io.github.phiscript.i18n.UiText.text("无障碍与 Shizuku")).setItems(io.github.phiscript.i18n.UiText.text(new String[]{tr("连接 Shizuku"),tr("通过 Shizuku 启用无障碍"),tr("查看 / 复制无障碍诊断"),tr("记录服务关闭原因")}),(d,i)->{
            if(i==0){try{io.github.phiscript.assets.ApkAccess.INSTANCE.requestPermission();}catch(Exception e){RootPanel.message(activity,"Shizuku",e.getMessage());}}
            else invoke(activity,i==1?"enableAccessibilityWithShizuku":i==2?"accessibilityHelp":"captureAccessibilityFailure");
        }).setNegativeButton(io.github.phiscript.i18n.UiText.text("关闭"),null).create());
    }
    public static void invoke(Activity a,String name){invoke(a,name,new Class<?>[0],new Object[0]);}
    private static void invoke(Activity a,String name,Class<?>[] types,Object[] values){
        try{Method m=a.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(a,values);}
        catch(Exception e){Throwable t=e instanceof InvocationTargetException?((InvocationTargetException)e).getTargetException():e;RootPanel.message(a,tr("操作未完成"),String.valueOf(t.getMessage()));}
    }
    private TextView field(String name){try{Field f=activity.getClass().getDeclaredField(name);f.setAccessible(true);return(TextView)f.get(activity);}catch(Exception e){RuntimeState.INSTANCE.log(tr("界面状态字段未读取：")+name);return null;}}
    private LinearLayout card(LinearLayout page){LinearLayout box=UiDesign.card(activity);page.addView(box,params(-1,-2,12));return box;}
    private void label(LinearLayout box,String title){TextView name=text(box,title,17,INK,true);if(android.os.Build.VERSION.SDK_INT>=28)name.setAccessibilityHeading(true);}
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold){TextView v=UiDesign.text(activity,value,size,color,bold);parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;}
    private Button action(LinearLayout parent,String value,boolean main,Runnable click){Button b=UiDesign.button(activity,value,main,click);parent.addView(b,params(-1,-2,12));return b;}
    private LinearLayout.LayoutParams params(int w,int h,int top){return UiDesign.space(activity,w,h,top);}
    private int dp(float n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
}
