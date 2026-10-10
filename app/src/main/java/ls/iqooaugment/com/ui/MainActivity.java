package ls.iqooaugment.com.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import ls.iqooaugment.com.BuildConfig;
import ls.iqooaugment.com.Constants;
import ls.iqooaugment.com.core.AppKey;
import ls.iqooaugment.com.core.ScanPolicy;
import ls.iqooaugment.com.core.UiMetrics;
import ls.iqooaugment.com.core.UiStatePolicy;
import ls.iqooaugment.com.data.AppEntry;
import ls.iqooaugment.com.data.Catalog;
import ls.iqooaugment.com.data.Config;
import ls.iqooaugment.com.data.Diagnostics;
import ls.iqooaugment.com.data.RuntimeStatus;
import ls.iqooaugment.com.data.Target;
import java.text.Collator;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final String HOME="home", MODULE="module", ABOUT="about", ADD="add";
    private static final String[] PAGES={HOME,MODULE,ABOUT,ADD};
    private String page=HOME, homeQuery="", addQuery="";
    private int homeSpace=-1, addSpace=-1;
    private final Set<String> picked=new LinkedHashSet<>(), expanded=new LinkedHashSet<>();
    private final Set<ScanPolicy.SpaceKey> scanScope=new LinkedHashSet<>();
    private boolean recoveringScopeSelection;
    private UiModel model;
    private AppearanceController appearance;
    private UiKit ui;
    private LinearLayout root;
    private View topBar,bottomNavigation;
    private ListView list;
    private ScrollView pageScroll;
    private final Map<String,Integer> pageScrollPositions=new LinkedHashMap<>();
    private EditText searchInput;
    private String renderedPage="";
    private final Map<String,int[]> scrollPositions=new LinkedHashMap<>();
    private boolean listRestorePending, pageRestorePending;
    private BaseAdapter adapter;
    private TextView count;
    private Button addButton;
    private final List<Object> displayed=new ArrayList<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private ContentObserver statusObserver, configObserver;
    private boolean visible, resumed, statusRegistered;
    private final LruCache<String,Bitmap> icons=new LruCache<>(160);
    private final Runnable statusRefresh=()-> { if (!visible) return; if (MODULE.equals(page)) render(); else if (HOME.equals(page)) refreshList(); };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model=getLastNonConfigurationInstance() instanceof UiModel?(UiModel)getLastNonConfigurationInstance():new UiModel(this);
        model.attach(this); appearance=new AppearanceController(this);
        if (state!=null) {
            page=state.getString("page",HOME); homeQuery=state.getString("hq",""); addQuery=state.getString("aq","");
            homeSpace=state.getInt("hs",-1); addSpace=state.getInt("as",-1);
            ArrayList<String> p=state.getStringArrayList("picked"), e=state.getStringArrayList("expanded");
            if(p!=null)picked.addAll(p); if(e!=null)expanded.addAll(e);
            if(!HOME.equals(page)&&!MODULE.equals(page)&&!ABOUT.equals(page)&&!ADD.equals(page))page=HOME;
            Bundle lists=state.getBundle("listPositions"), scrolls=state.getBundle("pagePositions");
            for(String destination:PAGES){
                if(lists!=null&&lists.getIntArray(destination)!=null)scrollPositions.put(destination,lists.getIntArray(destination));
                if(scrolls!=null&&scrolls.containsKey(destination))pageScrollPositions.put(destination,scrolls.getInt(destination));
            }
            if(lists==null&&state.containsKey("position"))scrollPositions.put(page,new int[]{state.getInt("position",0),state.getInt("offset",0)});
            if(scrolls==null)pageScrollPositions.put(page,state.getInt("pageScroll",0));
            String savedScope=state.getString("scanScope","");
            if(!savedScope.isEmpty())scanScope.addAll(ScanPolicy.parseSelection(savedScope));
            recoveringScopeSelection=state.getBoolean("recoveringScopeSelection",false);
        }
        render();
        statusObserver=new ContentObserver(main) {
            @Override public void onChange(boolean self) { if (visible) { main.removeCallbacks(statusRefresh); main.postDelayed(statusRefresh,250); } }
        };
        configObserver=new ContentObserver(main) {
            @Override public void onChange(boolean self) { if(visible)model.reloadConfig(); }
        };
    }
    @Override public Object onRetainNonConfigurationInstance(){ return model; }
    @Override protected void onSaveInstanceState(Bundle out) {
        captureScrollPositions();
        super.onSaveInstanceState(out); out.putString("page",page);out.putString("hq",homeQuery);out.putString("aq",addQuery);
        out.putInt("hs",homeSpace);out.putInt("as",addSpace);out.putStringArrayList("picked",new ArrayList<>(picked));
        out.putStringArrayList("expanded",new ArrayList<>(expanded));
        Bundle lists=new Bundle(), scrolls=new Bundle();
        for(Map.Entry<String,int[]> entry:scrollPositions.entrySet())lists.putIntArray(entry.getKey(),entry.getValue());
        for(Map.Entry<String,Integer> entry:pageScrollPositions.entrySet())scrolls.putInt(entry.getKey(),entry.getValue());
        out.putBundle("listPositions",lists);out.putBundle("pagePositions",scrolls);
        if(!scanScope.isEmpty())out.putString("scanScope",ScanPolicy.encodeSelection(scanScope));
        out.putBoolean("recoveringScopeSelection",recoveringScopeSelection);
    }
    @Override protected void onStart(){
        super.onStart(); visible=true;
        if(!statusRegistered){getContentResolver().registerContentObserver(Uri.parse("content://"+Constants.AUTHORITY+"/status"),false,statusObserver);getContentResolver().registerContentObserver(Uri.parse("content://"+Constants.AUTHORITY+"/config"),false,configObserver);statusRegistered=true;}
        model.reloadConfig();
        render();
    }
    @Override protected void onResume(){super.onResume();resumed=true;model.deliverPending();main.post(statusRefresh);}
    @Override protected void onPause(){resumed=false;super.onPause();}
    @Override protected void onStop(){
        visible=false;main.removeCallbacks(statusRefresh);
        if(statusRegistered){getContentResolver().unregisterContentObserver(statusObserver);getContentResolver().unregisterContentObserver(configObserver);statusRegistered=false;}
        super.onStop();
    }
    boolean canShowDialogs(){return resumed&&!isFinishing()&&!isDestroyed();}
    @Override protected void onDestroy(){
        if(statusRegistered){getContentResolver().unregisterContentObserver(statusObserver);getContentResolver().unregisterContentObserver(configObserver);statusRegistered=false;}main.removeCallbacksAndMessages(null);
        if(!isChangingConfigurations())model.close();super.onDestroy();
    }
    public void modelChanged(){if(model.catalog==null)icons.evictAll();rememberVerifiedScope();if(visible)render();}
    private void rememberVerifiedScope(){
        if(model.catalog==null||!model.catalog.appsRead||model.loading||!unavailableDraft().isEmpty())return;
        scanScope.clear();for(Catalog.Space space:model.catalog.spaces)if(space.scanned)scanScope.add(new ScanPolicy.SpaceKey(space.id,space.serial));
    }
    private void navigate(String destination){page=destination;render();}
    private void captureScrollPositions(){
        if(pageScroll!=null&&!pageRestorePending&&pageScroll.getHeight()>0&&!renderedPage.isEmpty())pageScrollPositions.put(renderedPage,pageScroll.getScrollY());
        if(list!=null&&!listRestorePending&&listHasRecoverableData(renderedPage)&&list.getChildCount()>0&&!renderedPage.isEmpty())scrollPositions.put(renderedPage,new int[]{list.getFirstVisiblePosition(),list.getChildAt(0).getTop()});
    }
    private boolean listHasRecoverableData(String destination){return !ADD.equals(destination)||(model.catalog!=null&&model.catalog.appsRead&&!model.loading);}
    private void render(){
        captureScrollPositions();
        int[] scroll=scrollPositions.get(page);int position=scroll==null?0:scroll[0],offset=scroll==null?0:scroll[1];
        boolean keepFocus=searchInput!=null&&searchInput.hasFocus()&&page.equals(renderedPage);
        int caret=keepFocus?searchInput.getSelectionStart():0;searchInput=null;
        ui=new UiKit(this,appearance);list=null;pageScroll=null;listRestorePending=false;pageRestorePending=false;topBar=null;bottomNavigation=null;adapter=null;count=null;addButton=null;displayed.clear();
        root=ui.column();root.setBackground(new IceBackdrop(ui.palette.dark));setContentView(root);applyInsets();
        if(ADD.equals(page))renderAdd();
        else {toolbar(false,HOME.equals(page));LinearLayout body=ui.column();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
            if(MODULE.equals(page))renderModule(body);else if(ABOUT.equals(page))renderAbout(body);else renderHome(body);bottomNav();}
        if(list!=null){
            ListView restored=list;String destination=page;listRestorePending=true;restored.setSelectionFromTop(position,offset);
            restored.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener(){
                @Override public void onGlobalLayout(){if(restored.getHeight()<=0||restored.getChildCount()==0||!listHasRecoverableData(destination))return;restored.getViewTreeObserver().removeOnGlobalLayoutListener(this);if(list==restored)listRestorePending=false;}
            });
        }
        if(pageScroll!=null){
            ScrollView restored=pageScroll;int y=pageScrollPositions.getOrDefault(page,0);pageRestorePending=true;
            restored.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener(){
                @Override public void onGlobalLayout(){if(restored.getHeight()<=0)return;restored.getViewTreeObserver().removeOnGlobalLayoutListener(this);restored.scrollTo(0,y);if(pageScroll==restored)pageRestorePending=false;}
            });
        }
        renderedPage=page;if(keepFocus&&searchInput!=null){searchInput.requestFocus();searchInput.setSelection(Math.max(0,Math.min(caret,searchInput.length())));}
    }
    private void applyInsets(){
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if(Build.VERSION.SDK_INT>=30){
            int light=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            if(getWindow().getInsetsController()!=null)getWindow().getInsetsController().setSystemBarsAppearance(ui.palette.dark?0:light,light);
        }else if(!ui.palette.dark)getWindow().getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility()|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());Insets ime=insets.getInsets(WindowInsets.Type.ime());view.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));updateKeyboardChrome(ime.bottom>bars.bottom);}
            else {view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());updateKeyboardChrome(insets.getSystemWindowInsetBottom()>ui.dp(100));}
            return insets;
        });root.requestApplyInsets();
    }
    private void updateKeyboardChrome(boolean keyboard){
        boolean landscape=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if(topBar!=null)topBar.setVisibility(keyboard&&landscape?View.GONE:View.VISIBLE);
        if(bottomNavigation!=null)bottomNavigation.setVisibility(keyboard?View.GONE:View.VISIBLE);
    }
    private void toolbar(boolean back,boolean add){
        LinearLayout row=ui.row();row.setPadding(ui.dp(back?8:20),ui.dp(12),ui.dp(12),0);
        if(back)row.addView(ui.iconAction(GlassIcon.Kind.BACK,"返回",this::onBackPressed));
        LinearLayout brand=ui.column();brand.setGravity(Gravity.CENTER_VERTICAL);brand.setMinimumHeight(ui.dp(56));
        TextView title=ui.text("IQOO肩键扩展",back?UiKit.TextRole.TITLE:UiKit.TextRole.HOME_TITLE);brand.addView(title);
        if(!back){TextView tagline=ui.text("让原生肩键，触达更多应用",UiKit.TextRole.CAPTION);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=ui.dp(4);brand.addView(tagline,lp);}
        row.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(ui.iconAction(add?GlassIcon.Kind.ADD:GlassIcon.Kind.INFO,add?"新增解限应用":"使用说明",()->{if(add)navigate(ADD);else help();}));topBar=row;root.addView(row);
    }
    private void bottomNav(){
        LinearLayout nav=ui.row();nav.setPadding(ui.dp(2),ui.dp(2),ui.dp(2),ui.dp(2));
        android.graphics.drawable.GradientDrawable glass=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{ui.palette.surface,ui.palette.surfaceEnd});glass.setCornerRadius(ui.dp(32));glass.setStroke(ui.dp(1),ui.palette.highlight);nav.setBackground(glass);
        String[] labels={"主页","模块","关于"};String[] destinations={HOME,MODULE,ABOUT};GlassIcon.Kind[] kinds={GlassIcon.Kind.HOME,GlassIcon.Kind.MODULE,GlassIcon.Kind.INFO};
        for(int i=0;i<3;i++){
            final String target=destinations[i];boolean selected=target.equals(page);LinearLayout item=ui.column();item.setGravity(Gravity.CENTER);item.setMinimumHeight(ui.dp(52));item.setPadding(0,ui.dp(4),0,ui.dp(4));
            int color=selected?ui.palette.accent:ui.palette.secondary;
            item.addView(new GlassIcon(this,kinds[i],color),new LinearLayout.LayoutParams(ui.dp(29),ui.dp(29)));
            TextView label=ui.text(labels[i],UiKit.TextRole.CAPTION);label.setTextColor(color);label.setGravity(Gravity.CENTER);item.addView(label,new LinearLayout.LayoutParams(-1,-2));item.setContentDescription(labels[i]);item.setSelected(selected);item.setFocusable(true);item.setBackground(ui.shapeRipple(selected?ui.palette.soft:0,30));item.setOnClickListener(v->navigate(target));
            nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(ui.dp(16),ui.dp(8),ui.dp(16),ui.dp(12));bottomNavigation=nav;root.addView(nav,lp);
    }
    private void renderHome(LinearLayout host){
        host.setPadding(ui.dp(16),0,ui.dp(16),0);
        if(homeSpace!=-1&&model.config.targets.stream().noneMatch(t->t.key.userId==homeSpace))homeSpace=-1;
        createList(host);LinearLayout header=ui.column();
        View search=search(homeQuery,"搜索已解限应用",false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=ui.dp(UiMetrics.HOME_TITLE_TO_SEARCH_DP);sp.bottomMargin=ui.dp(UiMetrics.HOME_SEARCH_TO_LIST_DP);header.addView(search,sp);
        LiquidGlassLayout heading=ui.preferenceCard();LinearLayout summary=ui.row();count=ui.text("",UiKit.TextRole.SECONDARY);count.setPadding(0,ui.dp(12),0,ui.dp(12));summary.addView(count,new LinearLayout.LayoutParams(0,-2,1));heading.addView(summary);
        if(model.config.targets.stream().map(t->t.key.userId).distinct().count()>1){ui.divider(heading);heading.addView(spaceChips(false));}
        header.addView(heading);list.addHeaderView(header,null,false);refreshList();
    }
    private View search(String value,String hint,boolean add){
        LiquidGlassLayout glass=ui.card();glass.setPadding(ui.dp(8),0,ui.dp(4),0);LinearLayout row=ui.row();row.addView(new GlassIcon(this,GlassIcon.Kind.SEARCH,ui.palette.secondary),new LinearLayout.LayoutParams(ui.dp(32),ui.dp(48)));
        EditText field=ui.input(hint);field.setBackgroundColor(Color.TRANSPARENT);field.setPadding(ui.dp(4),ui.dp(8),ui.dp(8),ui.dp(8));searchInput=field;field.setText(value);field.setSelectAllOnFocus(false);field.setContentDescription(hint);
        field.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){if(add)addQuery=s.toString();else homeQuery=s.toString();refreshList();}public void afterTextChanged(Editable e){}});
        row.addView(field,new LinearLayout.LayoutParams(0,-2,1));glass.addView(row);return glass;
    }
    private View spaceChips(boolean add){
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);LinearLayout chips=ui.row();
        Map<Integer,String> spaces=new LinkedHashMap<>();spaces.put(-1,add?"已读取空间":"全部空间");
        if(model.catalog!=null)for(Catalog.Space space:model.catalog.spaces)if(!add||space.scanned)spaces.put(space.id,space.title+" · u"+space.id);
        if(!add)for(Target t:model.config.targets)spaces.putIfAbsent(t.key.userId,t.space+" · u"+t.key.userId);
        for(Map.Entry<Integer,String> space:spaces.entrySet()){
            int id=space.getKey();boolean selected=id==(add?addSpace:homeSpace);
            Button chip=ui.action(space.getValue(),()->{if(add)addSpace=id;else homeSpace=id;render();});
            chip.setTextColor(selected?ui.palette.accent:ui.palette.secondary);chip.setBackground(ui.shapeRipple(selected?ui.palette.soft:0,6));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=ui.dp(8);chips.addView(chip,lp);
        }scroll.addView(chips);return scroll;
    }
    private void createList(LinearLayout host){
        list=new ListView(this);list.setItemsCanFocus(true);list.setDivider(null);list.setDividerHeight(0);list.setCacheColorHint(Color.TRANSPARENT);list.setSelector(android.R.color.transparent);list.setClipToPadding(false);list.setPadding(0,0,0,ui.dp(8));
        adapter=new BaseAdapter(){
            @Override public int getCount(){return displayed.size();}
            @Override public Object getItem(int position){return displayed.get(position);}
            @Override public long getItemId(int position){return position;}
            @Override public View getView(int position,View convertView,ViewGroup parent){Object item=displayed.get(position);if(item instanceof Target)return targetCard((Target)item);if(item instanceof AppEntry)return pickerCard((AppEntry)item);return messageCard(String.valueOf(item));}
        };list.setAdapter(adapter);host.addView(list,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void refreshList(){
        if(adapter==null)return;displayed.clear();
        if(ADD.equals(page)){
            List<AppEntry> entries=new ArrayList<>();String query=addQuery.trim().toLowerCase(Locale.ROOT);
            if(model.catalog!=null)for(AppEntry e:model.catalog.apps){
                if(model.config.find(e.key)!=null||(addSpace!=-1&&addSpace!=e.key.userId))continue;
                if(!query.isEmpty()&&!e.label.toLowerCase(Locale.ROOT).contains(query)&&!e.key.packageName.toLowerCase(Locale.ROOT).contains(query))continue;entries.add(e);
            }
            Collator collator=Collator.getInstance(Locale.CHINA);entries.sort((a,b)->collator.compare(a.label,b.label));displayed.addAll(entries);
            if(model.loading)displayed.add(0,model.readingApps?"正在读取所选空间的应用列表…":"正在读取设备信息和空间列表，不读取应用…");
            if(!model.scanError.isEmpty())displayed.add(0,"读取失败："+model.scanError);
            if(model.catalog==null&&!model.loading)displayed.add("授权 Root 后先选择空间，确认后才读取所选空间的第三方应用。");
            if(model.catalog!=null&&!model.catalog.appsRead&&!model.loading)displayed.add("尚未读取应用，请先选择要读取的空间。");
            if(model.catalog!=null)for(Catalog.Space s:model.catalog.spaces)if(!s.warning.isEmpty()&&(addSpace==-1||addSpace==s.id))displayed.add(s.title+" · u"+s.id+"："+s.warning);
            if(entries.isEmpty()&&model.catalog!=null&&model.catalog.appsRead&&!model.loading)displayed.add("此筛选下没有可新增的应用；已添加应用在主页管理。");
            if(count!=null)count.setText("可添加 "+entries.size()+" 项 · 已选择 "+picked.size()+" 项");
            if(addButton!=null){addButton.setText((model.config.master?"新增 ":"开启扩展并新增 ")+picked.size()+" 项");addButton.setEnabled(!picked.isEmpty()&&!model.saving&&!model.loading);}
        }else{
            List<Target> active=new ArrayList<>(),paused=new ArrayList<>();String query=homeQuery.trim().toLowerCase(Locale.ROOT);
            for(Target t:model.config.targets){if(homeSpace!=-1&&homeSpace!=t.key.userId)continue;if(!query.isEmpty()&&!t.label.toLowerCase(Locale.ROOT).contains(query)&&!t.key.packageName.toLowerCase(Locale.ROOT).contains(query))continue;(t.enabled?active:paused).add(t);}
            displayed.addAll(active);if(!paused.isEmpty()){displayed.add("已暂停 · "+paused.size()+" 项");displayed.addAll(paused);}
            if(displayed.isEmpty())displayed.add(model.config.targets.isEmpty()?"尚未添加应用":"没有匹配的应用");
            if(!model.configError.isEmpty())displayed.add(0,model.configError);
            if(count!=null)count.setText("已添加 "+model.config.targets.size()+" 项  ·  "+(model.config.master?"扩展已开启":"扩展已暂停"));
        }adapter.notifyDataSetChanged();
    }
    private View messageCard(String message){
        if(HOME.equals(page)&&model.config.targets.isEmpty()&&"尚未添加应用".equals(message)){
            LiquidGlassLayout card=ui.preferenceCard();card.setPadding(ui.dp(20),ui.dp(24),ui.dp(20),ui.dp(20));
            GlassIcon icon=new GlassIcon(this,GlassIcon.Kind.APPS,ui.palette.accent);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(ui.dp(44),ui.dp(44));ip.bottomMargin=ui.dp(12);card.addView(icon,ip);
            card.addView(ui.text("添加你的第一个应用",UiKit.TextRole.TITLE));TextView note=ui.text("主空间与分身分别选择，保留每个实例的开关。",UiKit.TextRole.SECONDARY);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=ui.dp(8);np.bottomMargin=ui.dp(18);card.addView(note,np);
            Button add=ui.action("新增解限应用",()->navigate(ADD));card.addView(add,new LinearLayout.LayoutParams(-2,-2));LinearLayout shell=ui.column();shell.addView(card);return shell;
        }
        TextView text=ui.text(message,UiKit.TextRole.SECONDARY);text.setPadding(ui.dp(4),ui.dp(12),ui.dp(4),ui.dp(12));return text;
    }
    private View appIcon(AppEntry app,String label){
        Bitmap bitmap=null;if(app!=null&&!app.icon.isEmpty())try{bitmap=icons.get(app.key.encode());if(bitmap==null){byte[] bytes=Base64.decode(app.icon,Base64.DEFAULT);bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap!=null)icons.put(app.key.encode(),bitmap);}}catch(Exception ignored){}
        View view;if(bitmap!=null){ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setScaleType(ImageView.ScaleType.FIT_CENTER);view=image;}
        else{TextView fallback=ui.text(label.isEmpty()?"应":label.substring(0,1),UiKit.TextRole.TITLE);fallback.setGravity(Gravity.CENTER);fallback.setTextColor(ui.palette.accent);fallback.setBackground(ui.ripple());view=fallback;}
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ui.dp(36),ui.dp(36));lp.rightMargin=ui.dp(12);view.setLayoutParams(lp);view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return view;
    }
    private View targetCard(Target target){
        LiquidGlassLayout card=ui.card();card.setPadding(ui.dp(12),ui.dp(4),ui.dp(8),ui.dp(4));LinearLayout row=ui.row();row.setMinimumHeight(ui.dp(68));
        AppEntry app=model.catalog==null?null:model.catalog.find(target.key);row.addView(appIcon(app,target.label));
        LinearLayout label=ui.column();label.addView(ui.text(target.label,UiKit.TextRole.TITLE));TextView instance=ui.text(target.space+" · u"+target.key.userId,UiKit.TextRole.CAPTION);instance.setPadding(0,ui.dp(4),0,0);label.addView(instance);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        IceSwitch toggle=new IceSwitch(this,ui.palette);toggle.setChecked(target.enabled);toggle.setEnabled(!model.saving);toggle.setContentDescription(target.label+"，"+target.space+"，肩键扩展");toggle.onRequest(value->setTarget(target,value));row.addView(toggle,new LinearLayout.LayoutParams(ui.dp(52),ui.dp(48)));
        boolean open=target.enabled&&expanded.contains(target.key.encode());View expand=ui.iconAction(open?GlassIcon.Kind.UP:GlassIcon.Kind.DOWN,open?"收起参数":"展开参数",()->{if(!target.enabled)return;if(open)expanded.remove(target.key.encode());else expanded.add(target.key.encode());refreshList();});
        expand.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(25),ui.dp(44)));expand.setEnabled(target.enabled);expand.setAlpha(target.enabled?1f:.35f);row.addView(expand);card.addView(row);
        row.setOnLongClickListener(v->{removeTarget(target);return true;});
        if(open){
            ui.divider(card);TextView packageName=ui.text(target.key.packageName,UiKit.TextRole.CAPTION);packageName.setPadding(ui.dp(4),ui.dp(10),ui.dp(4),0);card.addView(packageName);
            String status=!model.config.master?"已保存；总开关关闭，模块不扩展准入。":app==null&&model.catalog!=null&&model.catalog.completeFor(target.key.userId)?"此空间未找到应用；记录已保留，不会改用另一空间。":"已选择此空间的应用。进入应用后使用原生游戏侧边栏配置肩键。";
            TextView note=ui.text(status,UiKit.TextRole.SECONDARY);note.setPadding(ui.dp(4),ui.dp(8),ui.dp(4),ui.dp(4));card.addView(note);
            RuntimeStatus runtime=new RuntimeStatus(this);if(target.key.encode().equals(runtime.daemon.optString("lastAdmission"))){TextView hit=ui.text("最近准入："+time(runtime.daemon.optLong("lastAdmissionWall"))+"（不等同于触摸验证）",UiKit.TextRole.CAPTION);hit.setPadding(ui.dp(4),0,0,0);card.addView(hit);}
            LinearLayout remove=ui.actionRow(GlassIcon.Kind.REMOVE,"移除解限记录",null,()->removeTarget(target));card.addView(remove);
        }
        LinearLayout shell=ui.column();shell.addView(card);return shell;
    }
    private View pickerCard(AppEntry app){
        LiquidGlassLayout card=ui.card();card.setPadding(ui.dp(12),ui.dp(8),ui.dp(8),ui.dp(8));LinearLayout row=ui.row();row.setMinimumHeight(ui.dp(56));row.addView(appIcon(app,app.label));
        LinearLayout label=ui.column();label.addView(ui.text(app.label,UiKit.TextRole.TITLE));label.addView(ui.text(app.space+" · u"+app.key.userId,UiKit.TextRole.CAPTION));label.addView(ui.text(app.key.packageName,UiKit.TextRole.CAPTION));if(!app.eligible)label.addView(ui.text(app.reason,UiKit.TextRole.SECONDARY));row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        CheckBox check=new CheckBox(this);check.setChecked(picked.contains(app.key.encode()));check.setButtonTintList(ui.enabledColors(ui.palette.accent));check.setEnabled(app.eligible&&!model.saving&&!model.loading);check.setMinWidth(ui.dp(48));check.setMinHeight(ui.dp(48));check.setContentDescription("选择 "+app.label+"，"+app.space+" u"+app.key.userId);
        Runnable change=()->{if(!app.eligible||model.saving||model.loading)return;String key=app.key.encode();Set<String> unavailable=unavailableDraft();if(!picked.contains(key)&&!unavailable.isEmpty()){resolveDraft(unavailable);return;}if(picked.contains(key))picked.remove(key);else picked.add(key);rememberVerifiedScope();refreshList();};check.setOnClickListener(v->change.run());row.setOnClickListener(v->change.run());row.addView(check,new LinearLayout.LayoutParams(ui.dp(48),-2));card.addView(row);LinearLayout shell=ui.column();shell.addView(card);return shell;
    }
    private void renderAdd(){
        toolbar(true,false);LinearLayout body=ui.detailChrome(root,"主页","新增解限应用",()->leaveAdd(HOME));createList(body);LinearLayout header=ui.column();
        header.addView(search(addQuery,"搜索应用名称或包名",true));
        LiquidGlassLayout scan=ui.preferenceCard();LinearLayout read=ui.actionRow(GlassIcon.Kind.REFRESH,model.loading?"正在读取…":draftNeedsVerification()?"保留草稿并重新核验":model.catalog==null?"授权 Root 并选择空间":"重新选择读取空间","仅在确认后读取所选空间的应用",this::prepareAppScan);read.setEnabled(!model.loading&&!model.saving);read.setAlpha(read.isEnabled()?1f:.55f);scan.addView(read);
        Set<String> unavailable=unavailableDraft();
        if(!unavailable.isEmpty()){ui.divider(scan);LinearLayout resolve=ui.actionRow(GlassIcon.Kind.INFO,"检查 "+unavailable.size()+" 项不可用草稿","选择已保留；确认后才移除不可用或已添加的项",()->resolveDraft(unavailable));resolve.setEnabled(!model.loading&&!model.saving);scan.addView(resolve);}
        header.addView(scan);
        if(model.catalog!=null&&model.catalog.appsRead)header.addView(spaceChips(true));count=ui.text("",UiKit.TextRole.SECONDARY);count.setPadding(0,ui.dp(4),0,ui.dp(8));header.addView(count);list.addHeaderView(header,null,false);
        addButton=ui.primaryAction("新增",this::addPicked);LinearLayout.LayoutParams action=new LinearLayout.LayoutParams(-1,-2);action.topMargin=ui.dp(8);action.bottomMargin=ui.dp(12);body.addView(addButton,action);refreshList();
    }
    private void prepareAppScan(){
        if(model.loading||model.saving)return;
        if(draftNeedsVerification()){confirmDraftVerification();return;}
        if(!picked.isEmpty()){
            ui.dialog().setTitle("重新选择读取空间？").setMessage("将清除这 "+picked.size()+" 项尚未保存的应用选择，并重新确认本次读取范围。已保存的解限记录不变。")
                .setNegativeButton("继续选择应用",null).setPositiveButton("重新选择空间",(d,w)->startAppScanSelection()).show();
            return;
        }
        startAppScanSelection();
    }
    private void startAppScanSelection(){
        if(model.loading||model.saving)return;
        captureScrollPositions();picked.clear();scanScope.clear();recoveringScopeSelection=false;icons.evictAll();addSpace=-1;model.prepareScan();
    }
    private boolean draftNeedsVerification(){return !picked.isEmpty()&&(model.catalog==null||!model.catalog.appsRead);}
    private void confirmDraftVerification(){
        final Set<ScanPolicy.SpaceKey> scope;
        try{scope=UiStatePolicy.recoveryScope(picked,scanScope);}catch(IllegalArgumentException error){showError("无法重新核验",error.getMessage());return;}
        StringBuilder spaces=new StringBuilder();for(ScanPolicy.SpaceKey space:scope){if(spaces.length()>0)spaces.append("、");spaces.append("u").append(space.userId).append("（序列 ").append(space.serial).append("）");}
        ui.dialog().setTitle("保留草稿并重新核验？").setMessage("已恢复 "+picked.size()+" 项尚未新增的选择。Root 授权和应用目录不会恢复，也不会自动读取。\n\n确认后重新授权 Root，只读取原确认范围："+spaces+"。读取失败、空间变化或应用不可用时保留草稿。也可以先检查可用空间，再明确缩小本次读取范围；无法核验的选择由你处理。")
            .setNegativeButton("取消",null).setNeutralButton("检查可用空间",(d,w)->{if(model.loading||model.saving)return;scanScope.clear();scanScope.addAll(scope);recoveringScopeSelection=true;model.prepareScan();})
            .setPositiveButton("按原范围读取",(d,w)->{if(model.loading||model.saving)return;scanScope.clear();scanScope.addAll(scope);recoveringScopeSelection=false;icons.evictAll();model.scan(scope);}).show();
    }
    private Set<String> unavailableDraft(){
        if(model.loading||model.catalog==null||!model.catalog.appsRead)return Collections.emptySet();
        Set<String> eligible=new LinkedHashSet<>(), saved=new LinkedHashSet<>();
        for(AppEntry app:model.catalog.apps)if(app.eligible)eligible.add(app.key.encode());
        for(Target target:model.config.targets)saved.add(target.key.encode());
        return UiStatePolicy.unavailableDraft(picked,eligible,saved);
    }
    private void resolveDraft(Set<String> unavailable){
        StringBuilder details=new StringBuilder();for(String value:unavailable){AppKey key=AppKey.decode(value);if(details.length()>0)details.append('\n');details.append(key.packageName).append(" · u").append(key.userId).append("（序列 ").append(key.serial).append("）");}
        ui.dialog().setTitle("处理不可用草稿？").setMessage("以下应用已添加、不可用，或其原空间身份无法核验。其余选择继续保留。\n\n"+details)
            .setNegativeButton("保留草稿",null).setPositiveButton("仅移除这些选择",(d,w)->{if(model.loading||model.saving)return;Set<String> stillUnavailable=new LinkedHashSet<>(unavailableDraft());stillUnavailable.retainAll(unavailable);picked.removeAll(stillUnavailable);rememberVerifiedScope();render();}).show();
    }
    void showScanSelection(){
        if(!ADD.equals(page)||model.catalog==null||model.loading||model.saving)return;
        Catalog snapshot=model.catalog;
        boolean restoring=recoveringScopeSelection&&!picked.isEmpty();
        LinearLayout content=ui.column();content.setPadding(ui.dp(20),ui.dp(8),ui.dp(20),ui.dp(8));
        content.addView(ui.text(restoring?"草稿继续保留。默认只勾选仍可核验的原范围；你可以缩小本次读取范围。未读取或身份变化的草稿不会替换到其他空间，读取后由你明确处理。":"确认后仅读取勾选空间中第三方应用的名称、包名和图标。工作、私密及其他空间默认不勾选；选择仅用于本次读取。",UiKit.TextRole.SECONDARY));
        List<CheckBox> boxes=new ArrayList<>();
        for(Catalog.Space space:snapshot.spaces){
            CheckBox box=new CheckBox(this);boolean available=space.unlocked&&space.serial>=0;
            box.setText(space.title+" · u"+space.id+(available?"":"（未解锁或身份未核验）"));
            box.setTextColor(ui.enabledColors(ui.palette.text));box.setButtonTintList(ui.enabledColors(ui.palette.accent));
            box.setMinHeight(ui.dp(48));box.setChecked(restoring?available&&scanScope.contains(new ScanPolicy.SpaceKey(space.id,space.serial)):space.defaultSelected);box.setEnabled(available);
            boxes.add(box);content.addView(box,new LinearLayout.LayoutParams(-1,-2));
        }
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        AlertDialog dialog=ui.dialog().setTitle("选择要读取应用的空间").setView(scroll)
            .setNegativeButton("取消",null).setPositiveButton("读取所选空间",null).create();
        dialog.show();Button confirm=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Runnable update=()->{boolean any=false;for(CheckBox box:boxes)if(box.isEnabled()&&box.isChecked())any=true;confirm.setEnabled(any);};
        for(CheckBox box:boxes)box.setOnCheckedChangeListener((button,checked)->update.run());
        update.run();confirm.setOnClickListener(v->{
            if(model.loading||model.saving||model.catalog!=snapshot){dialog.dismiss();return;}
            Set<ScanPolicy.SpaceKey> selected=new LinkedHashSet<>();
            for(int i=0;i<boxes.size();i++)if(boxes.get(i).isEnabled()&&boxes.get(i).isChecked()){
                Catalog.Space space=snapshot.spaces.get(i);selected.add(new ScanPolicy.SpaceKey(space.id,space.serial));
            }
            if(selected.isEmpty())return;
            dialog.dismiss();captureScrollPositions();if(!restoring){picked.clear();scanScope.clear();scanScope.addAll(selected);}recoveringScopeSelection=false;icons.evictAll();addSpace=-1;model.scan(selected);
        });
    }
    private LinearLayout scrollContent(LinearLayout host){
        host.setPadding(ui.dp(16),ui.dp(22),ui.dp(16),0);pageScroll=new ScrollView(this);pageScroll.setFillViewport(false);pageScroll.setClipToPadding(false);pageScroll.setPadding(0,0,0,ui.dp(4));pageScroll.setVerticalScrollBarEnabled(false);LinearLayout content=ui.column();pageScroll.addView(content);host.addView(pageScroll,new LinearLayout.LayoutParams(-1,-1));return content;
    }
    private void renderModule(LinearLayout host){
        LinearLayout content=scrollContent(host);RuntimeStatus runtime=new RuntimeStatus(this);
        boolean ready=enableBlocker().isEmpty();
        LiquidGlassLayout state=ui.preferenceCard();state.setBackground(ui.shapeRipple(ready?ui.palette.soft:ui.palette.warning,4));
        LinearLayout stateRow=ui.preferenceRow(ready?"模块已就绪":"等待完成接入",ready?(model.config.master?"肩键准入扩展已开启 · 操作由原厂处理":"接入正常 · 不再追加肩键准入"):(model.rootReady?"Root 已授权":"Root 待核验")+"  ·  "+(runtime.ready()?"Hook 已就绪":"Hook 待接入"),null);stateRow.addView(ui.leadingIcon(GlassIcon.Kind.PULSE),0);state.addView(stateRow);content.addView(state);
        ui.addDetailSectionTitle(content,"模块设置",false);LiquidGlassLayout feature=ui.preferenceCard();IceSwitch master=new IceSwitch(this,ui.palette);master.setChecked(model.config.master);master.setEnabled(!model.saving);master.setContentDescription("肩键扩展总开关");master.onRequest(value->{if(value&&!ensureReady())return;model.save(model.config.withMaster(value),null);});
        LinearLayout featureRow=ui.featureRow("启用肩键扩展",model.config.master?"仅为所选实例扩展原生准入":"暂停观察，保留已选应用",master);featureRow.addView(ui.leadingIcon(GlassIcon.Kind.MODULE),0);feature.addView(featureRow);content.addView(feature);TextView nativeNotice=ui.text("修改开关或应用选择前，先松开肩键并退出目标应用。已开启的原生映射由原厂关闭；模块不会强制停止现有输出。更改后重新进入应用。",UiKit.TextRole.SECONDARY);nativeNotice.setPadding(0,ui.dp(8),0,ui.dp(4));content.addView(nativeNotice);
        ui.addDetailSectionTitle(content,"接入状态",false);LiquidGlassLayout status=ui.preferenceCard();
        status.addView(ui.valueRow("Root 权限",model.loading?"正在核验…":model.rootReady?"本次读取已授权":"尚未核验"));ui.divider(status);
        status.addView(ui.valueRow("游戏魔盒",model.catalog==null?"等待读取版本":model.catalog.cubeVersion+(model.catalog.compatible?" · 匹配":" · 未适配")));ui.divider(status);
        status.addView(ui.valueRow("LSPosed",runtime.ready()?"Hook 已接入":"Hook 待接入"));ui.divider(status);
        status.addView(ui.valueRow("肩键界面进程",runtime.assistantFresh?"存活身份已确认":"尚未确认存活身份"));
        TextView note=ui.text(runtime.message(),UiKit.TextRole.SECONDARY);note.setPadding(0,ui.dp(4),0,ui.dp(12));status.addView(note);
        if(!model.scanError.isEmpty()){TextView error=ui.text(model.scanError,UiKit.TextRole.SECONDARY);error.setTextColor(ui.palette.danger);error.setPadding(0,0,0,ui.dp(12));status.addView(error);}content.addView(status);
        ui.addDetailSectionTitle(content,"接入管理",false);LiquidGlassLayout actions=ui.preferenceCard();LinearLayout scan=ui.actionRow(GlassIcon.Kind.REFRESH,model.loading?"正在读取设备信息…":"授权并刷新设备信息","核验 Root 与版本，不读取应用列表",model::inspect);scan.setEnabled(!model.loading&&!model.saving);scan.setAlpha(scan.isEnabled()?1f:.55f);actions.addView(scan);ui.divider(actions);
        actions.addView(ui.actionRow(GlassIcon.Kind.SCOPE,"作用域设置说明","仅勾选游戏魔盒",this::scopeHelp));ui.divider(actions);actions.addView(ui.actionRow(GlassIcon.Kind.COPY,"复制脱敏诊断","仅导出状态摘要",this::copyDiagnostics));content.addView(actions);
    }
    private void renderAbout(LinearLayout host){
        LinearLayout content=scrollContent(host);LiquidGlassLayout device=ui.preferenceCard();TextView heading=ui.text("当前版本",UiKit.TextRole.CAPTION);heading.setTextColor(ui.palette.accent);heading.setPadding(0,ui.dp(12),0,ui.dp(4));device.addView(heading);device.addView(ui.valueRow("IQOO肩键扩展",BuildConfig.VERSION_NAME));device.addView(ui.valueRow("版本代码",String.valueOf(BuildConfig.VERSION_CODE)));device.addView(ui.valueRow("适配设备","iQOO 15 Ultra"));device.addView(ui.valueRow("目标游戏魔盒",Constants.CUBE_VERSION));content.addView(device);
        LiquidGlassLayout guide=ui.preferenceCard();guide.addView(ui.actionRow(GlassIcon.Kind.INFO,"使用说明与支持范围","通过原生游戏侧边栏配置肩键",this::help));ui.divider(guide);guide.addView(ui.actionRow(GlassIcon.Kind.SHIELD,"隐私与本地运行","无联网权限 · 无统计 SDK · 无账号",this::privacyHelp));content.addView(guide);
        TextView footnote=ui.text("按应用与空间分别管理 · 原生肩键准入扩展",UiKit.TextRole.CAPTION);footnote.setGravity(Gravity.CENTER);footnote.setPadding(ui.dp(8),ui.dp(12),ui.dp(8),ui.dp(12));content.addView(footnote);
    }
    private void privacyHelp(){ui.dialog().setTitle("隐私与本地运行").setMessage("应用无联网权限、无统计 SDK、无账号登录。\n\nRoot 仅按你确认的空间读取第三方应用。工作、私密及其他空间默认不选；设备信息刷新不读取应用列表。\n\n总开关关闭后停止前台观察，只保留配置变更通知与进程存活连接；没有周期续期。已开启的肩键由原厂生命周期关闭。\n\n诊断仅复制脱敏摘要，不含应用名称、包名、空间标识或前台记录。").setPositiveButton("知道了",null).show();}
    private boolean ensureReady(){String reason=enableBlocker();if(reason.isEmpty())return true;showError("暂不能开启",reason);return false;}
    private String enableBlocker(){
        if(!model.configError.isEmpty())return model.configError;if(model.loading||model.saving)return "正在处理当前操作，请勿重复提交。";
        if(!model.rootReady||model.catalog==null)return "先在“模块”页授权 Root 并刷新设备信息，或在新增页面读取应用。";
        if(Process.myUid()/100000!=model.catalog.mainUser)return "请在主空间 u"+model.catalog.mainUser+" 安装并打开本模块管理应用；不要在分身中另建一套配置。";
        if(!model.catalog.compatible)return "当前游戏魔盒为 "+model.catalog.cubeVersion+"；本版仅适配 "+Constants.CUBE_VERSION+"。未对未知版本安装扩展 Hook。";
        RuntimeStatus runtime=new RuntimeStatus(this);if(!runtime.ready())return runtime.message();return "";
    }
    private void setTarget(Target target,boolean enabled){
        if(enabled){if(!ensureReady())return;AppEntry app=model.catalog.find(target.key);if(app==null||!app.eligible){showError("应用不可用","请解锁对应空间并刷新列表，确认该应用仍安装且未停用。不会自动替换为主空间应用。");return;}}
        List<Target> targets=new ArrayList<>();for(Target t:model.config.targets)targets.add(t.key.equals(target.key)?t.withEnabled(enabled):t);
        model.save(model.config.withTargets(targets),view->{if(enabled)view.expanded.add(target.key.encode());else view.expanded.remove(target.key.encode());});
    }
    private void addPicked(){
        if(picked.isEmpty()||!ensureReady())return;Map<AppKey,Target> targets=new LinkedHashMap<>();for(Target t:model.config.targets)targets.put(t.key,t);
        Set<String> unavailable=unavailableDraft();if(!unavailable.isEmpty()){resolveDraft(unavailable);return;}
        for(String value:picked){AppKey key=AppKey.decode(value);AppEntry app=model.catalog.find(key);if(app==null||!app.eligible){showError("无法新增","应用列表已变化，请刷新并重新选择。原有记录未修改。");return;}targets.put(key,new Target(key,app.label,app.space,true));}
        if(targets.size()>Constants.MAX_TARGETS){showError("数量超限","最多保留 "+Constants.MAX_TARGETS+" 个应用空间实例。原有记录未修改。");return;}
        Set<String> addedKeys=new LinkedHashSet<>(picked);Config next=new Config(true,model.config.revision+1,new ArrayList<>(targets.values()));model.save(next,view->{view.expanded.addAll(addedKeys);view.picked.clear();view.page=HOME;view.toast("应用已保存，重新进入应用后使用原生肩键菜单");});
    }
    private void removeTarget(Target target){
        ui.dialog().setTitle("移除解限记录？").setMessage(target.label+" · "+target.space+" u"+target.key.userId+"\n只移除模块记录，不卸载应用，也不删除原生肩键映射。")
            .setNegativeButton("取消",null).setPositiveButton("移除",(d,w)->{if(model.saving)return;List<Target> next=new ArrayList<>();for(Target t:model.config.targets)if(!t.key.equals(target.key))next.add(t);model.save(model.config.withTargets(next),view->view.expanded.remove(target.key.encode()));}).show();
    }
    private void leaveAdd(String destination){
        if(model.saving)return;if(picked.isEmpty()){navigate(destination);return;}
        ui.dialog().setTitle("放弃未新增的选择？").setMessage("这 "+picked.size()+" 项尚未保存或生效。").setNegativeButton("继续选择",null).setPositiveButton("放弃",(d,w)->{picked.clear();navigate(destination);}).show();
    }
    @Override public void onBackPressed(){if(model.saving)return;if(ADD.equals(page))leaveAdd(HOME);else if(!HOME.equals(page))navigate(HOME);else super.onBackPressed();}
    private void scopeHelp(){ui.dialog().setTitle("LSPosed 作用域").setMessage("在与你的系统兼容的 LSPosed 系框架中启用本模块，并勾选“游戏魔盒” com.vivo.gamecube，然后重启设备。\n\n无需勾选 Android 系统、SystemUI 或每个目标应用。若固件在其他用户下单独运行游戏魔盒，也需在框架中确认对应用户的游戏魔盒进程已被覆盖。\n\n界面根据当前有效的进程连接和实际 Hook 状态判断接入；没有周期心跳续期。").setPositiveButton("知道了",null).setNeutralButton("打开管理器",(d,w)->{Intent intent=getPackageManager().getLaunchIntentForPackage("org.lsposed.manager");if(intent!=null)startActivity(intent);else toast("未找到独立管理器入口，请从你的框架入口打开");}).show();}
    private void help(){ui.dialog().setTitle("原生肩键支持范围").setMessage("1. 授权 Root 读取应用，启用模块并将游戏魔盒加入作用域。\n2. 在“新增解限应用”中按空间选择目标；主应用和分身分别保存。\n3. 进入可横屏的目标应用，从原生游戏侧边栏使用完整映射菜单。\n\n模块只扩展准入与肩键菜单分类；单击、长按、连点、按下/抬起、多操作的执行，以及保存和关闭都由原厂处理。保留原生硬件、方向、温控、权限与冲突检查。\n\n修改开关或选择前，先松开肩键并退出目标应用，再重新进入。模块不强制关闭已开启的原生输出。王者等专用映射仍活动或对应原生空间无法确认时，不切换为通用分类；先关闭原生肩键菜单并退出应用。\n\n空间识别失败时不追加准入。原生方案可能仍按包名共享，不承诺同包名跨空间具有独立坐标。没有真机触摸、释放或功耗测试。异常卡键或发热时松开肩键，禁用模块并重启。").setPositiveButton("知道了",null).show();}
    private void copyDiagnostics(){
        ui.dialog().setTitle("复制脱敏诊断？")
            .setMessage("包含：模块版本、Android SDK 级别、总开关、已选/启用数量、Root 本次核验状态、Hook 就绪布尔值及是否出现错误。\n\n不包含：机型、应用名称/包名、空间名称/编号/序列号、前台应用、进程编号、时间记录及错误原文。摘要将写入系统剪贴板，可在分享前查看。")
            .setNegativeButton("取消",null).setPositiveButton("复制摘要",(d,w)->{
                try{
                    JSONObject json=Diagnostics.summary(BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE,
                        Build.VERSION.SDK_INT,model.config,model.rootReady,new RuntimeStatus(this));
                    ClipboardManager clipboard=getSystemService(ClipboardManager.class);
                    if(clipboard==null)throw new IllegalStateException("剪贴板不可用");
                    clipboard.setPrimaryClip(ClipData.newPlainText("IQOO肩键扩展脱敏诊断",json.toString(2)));
                    toast("已复制脱敏摘要，不含应用与空间标识");
                }catch(Exception e){showError("复制失败",e.getMessage());}
            }).show();
    }
    public void showError(String title,String message){if(!isFinishing())ui.dialog().setTitle(title).setMessage(message==null?"未知错误":message).setPositiveButton("知道了",null).show();}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    private static String time(long value){return value<=0?"无记录":DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,Locale.CHINA).format(new java.util.Date(value));}
}
