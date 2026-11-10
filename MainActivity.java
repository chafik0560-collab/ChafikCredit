package com.chafiktech.credit;

import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.*;
import android.widget.*;
import java.text.DecimalFormat;
import java.util.*;

public class MainActivity extends Activity {
    private final int BG=Color.rgb(11,18,32), CARD=Color.rgb(23,38,58), CYAN=Color.rgb(0,212,255);
    private DB db;
    private LinearLayout content;
    private TextView totalDebt, totalPaid, totalRemain;
    private DecimalFormat fmt = new DecimalFormat("#,##0.00");

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        db=new DB(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildHome();
    }

    private void buildHome() {
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(BG);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18),dp(22),dp(18),dp(24));
        scroll.addView(content);
        setContentView(scroll);

        TextView brand=text("CHAFIK-TECH",26,CYAN,true); brand.setGravity(Gravity.CENTER);
        content.addView(brand);
        TextView sub=text("SMART CREDIT MANAGER",12,0xff8BABC5,true);
        sub.setGravity(Gravity.CENTER); content.addView(sub,lp(-1,dp(28)));

        LinearLayout cards=new LinearLayout(this); cards.setOrientation(LinearLayout.VERTICAL);
        content.addView(cards,lp(-1,-2));
        totalDebt=addStat(cards,"إجمالي الديون",CYAN);
        totalPaid=addStat(cards,"المبالغ المستخلصة",0xff4ADE80);
        totalRemain=addStat(cards,"الديون المتبقية",0xffffc857);

        addButton("＋ إضافة زبون جديد",()->addCustomer());
        addButton("＋ تسجيل دين جديد",()->addDebt());
        addButton("💰 تسجيل تسديد",()->addPayment());
        addButton("👥 قائمة الزبائن",()->showCustomers());
        addButton("📒 سجل التسديدات",()->showPayments());
        TextView foot=text("CHAFIK CREDIT  •  الإصدار 1.0",11,0xff8BABC5,false);
        foot.setGravity(Gravity.CENTER); content.addView(foot,lp(-1,dp(44)));
        refreshStats();
    }

    private TextView addStat(LinearLayout parent,String label,int color) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16),dp(12),dp(16),dp(12)); box.setBackground(round(CARD,16));
        LinearLayout.LayoutParams p=lp(-1,-2); p.bottomMargin=dp(10); parent.addView(box,p);
        TextView l=text(label,14,0xffd5e4f2,true); box.addView(l);
        TextView v=text("0.00 DA",22,color,true); box.addView(v);
        return v;
    }

    private void refreshStats() {
        double debt=db.sum("SELECT COALESCE(SUM(amount),0) FROM debts");
        double paid=db.sum("SELECT COALESCE(SUM(amount),0) FROM payments");
        totalDebt.setText(money(debt)+" DA");
        totalPaid.setText(money(paid)+" DA");
        totalRemain.setText(money(debt-paid)+" DA");
    }

    private void addButton(String label,Runnable action) {
        Button b=new Button(this); b.setText(label); b.setTextColor(Color.WHITE);
        b.setTextSize(16); b.setAllCaps(false); b.setBackground(round(CARD,14));
        LinearLayout.LayoutParams p=lp(-1,dp(56)); p.topMargin=dp(9); content.addView(b,p);
        b.setOnClickListener(v->action.run());
    }

    private void addCustomer() {
        LinearLayout form=form();
        EditText name=field("اسم الزبون الكامل"), phone=field("رقم الهاتف");
        form.addView(name); form.addView(phone);
        new AlertDialog.Builder(this).setTitle("إضافة زبون جديد").setView(form)
          .setNegativeButton("إلغاء",null).setPositiveButton("حفظ",(d,w)->{
            String n=name.getText().toString().trim(), ph=phone.getText().toString().trim();
            if(n.isEmpty()){toast("أدخل اسم الزبون");return;}
            db.exec("INSERT INTO customers(name,phone,created) VALUES(?,?,?)",new Object[]{n,ph,System.currentTimeMillis()});
            toast("تم حفظ الزبون بنجاح"); refreshStats();
          }).show();
    }

    private void addDebt() {
        List<Item> customers=db.customers();
        if(customers.isEmpty()){toast("أضف زبونًا أولًا");return;}
        LinearLayout form=form();
        Spinner spinner=spinner(customers); form.addView(spinner);
        EditText amount=field("مبلغ الدين بالدينار"); amount.setInputType(8194); form.addView(amount);
        EditText note=field("ملاحظة (اختياري)"); form.addView(note);
        new AlertDialog.Builder(this).setTitle("تسجيل دين جديد").setView(form)
          .setNegativeButton("إلغاء",null).setPositiveButton("حفظ",(d,w)->{
            Double a=parse(amount.getText().toString());
            if(a==null||a<=0){toast("أدخل مبلغًا صحيحًا");return;}
            Item c=(Item)spinner.getSelectedItem();
            db.exec("INSERT INTO debts(customer_id,amount,note,created) VALUES(?,?,?,?)",
              new Object[]{c.id,a,note.getText().toString().trim(),System.currentTimeMillis()});
            toast("تم تسجيل الدين"); refreshStats();
          }).show();
    }

    private void addPayment() {
        List<Item> customers=db.customers();
        ArrayList<Item> owing=new ArrayList<>();
        for(Item c:customers) if(db.balance(c.id)>0.001) owing.add(c);
        if(owing.isEmpty()){toast("لا توجد ديون متبقية");return;}
        LinearLayout form=form();
        Spinner spinner=spinner(owing); form.addView(spinner);
        TextView bal=text("سيتم تسجيل التسديد على حساب الزبون المختار",13,0xff8BABC5,false);
        form.addView(bal);
        EditText amount=field("مبلغ التسديد بالدينار"); amount.setInputType(8194); form.addView(amount);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                Item c=owing.get(pos); bal.setText("الدين المتبقي: "+money(db.balance(c.id))+" DA");
            } public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        new AlertDialog.Builder(this).setTitle("تسجيل تسديد").setView(form)
          .setNegativeButton("إلغاء",null).setPositiveButton("حفظ",(d,w)->{
            Item c=(Item)spinner.getSelectedItem(); Double a=parse(amount.getText().toString());
            double remaining=db.balance(c.id);
            if(a==null||a<=0){toast("أدخل مبلغًا صحيحًا");return;}
            if(a>remaining+0.001){toast("المبلغ أكبر من الدين المتبقي");return;}
            db.exec("INSERT INTO payments(customer_id,amount,created) VALUES(?,?,?)",
              new Object[]{c.id,a,System.currentTimeMillis()});
            toast("تم تسجيل التسديد"); refreshStats();
          }).show();
    }

    private void showCustomers() {
        List<Item> all=db.customers();
        LinearLayout box=form();
        EditText search=field("ابحث عن زبون"); box.addView(search);
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); box.addView(list);
        Runnable render=()->{
            list.removeAllViews(); String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
            for(Item c:all) if(c.name.toLowerCase(Locale.ROOT).contains(q)||c.phone.toLowerCase(Locale.ROOT).contains(q)){
                TextView row=text(c.name+"  •  "+money(db.balance(c.id))+" DA\n"+c.phone,15,Color.WHITE,true);
                row.setPadding(dp(12),dp(12),dp(12),dp(12)); row.setBackground(round(CARD,10));
                LinearLayout.LayoutParams p=lp(-1,-2); p.bottomMargin=dp(7); list.addView(row,p);
                row.setOnClickListener(v->showCustomerDetails(c));
            }
            if(list.getChildCount()==0) list.addView(text("لا يوجد زبائن",14,0xff8BABC5,false));
        };
        render.run(); search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            public void onTextChanged(CharSequence s,int st,int before,int count){render.run();}
            public void afterTextChanged(Editable e){}
        });
        new AlertDialog.Builder(this).setTitle("قائمة الزبائن").setView(box)
          .setPositiveButton("إغلاق",null).show();
    }

    private void showCustomerDetails(Item c) {
        StringBuilder s=new StringBuilder();
        s.append("الاسم: ").append(c.name).append("\nالهاتف: ").append(c.phone)
         .append("\n\nإجمالي الديون: ").append(money(db.customerSum("debts",c.id))).append(" DA")
         .append("\nالتسديدات: ").append(money(db.customerSum("payments",c.id))).append(" DA")
         .append("\nالمتبقي: ").append(money(db.balance(c.id))).append(" DA\n\nسجل الديون:\n");
        Cursor cur=db.getReadableDatabase().rawQuery("SELECT amount,note,created FROM debts WHERE customer_id=? ORDER BY id DESC",new String[]{""+c.id});
        while(cur.moveToNext()) s.append("• ").append(money(cur.getDouble(0))).append(" DA — ").append(cur.getString(1)).append("\n");
        cur.close();
        s.append("\nسجل التسديدات:\n");
        cur=db.getReadableDatabase().rawQuery("SELECT amount,created FROM payments WHERE customer_id=? ORDER BY id DESC",new String[]{""+c.id});
        while(cur.moveToNext()) s.append("• ").append(money(cur.getDouble(0))).append(" DA\n");
        cur.close();
        new AlertDialog.Builder(this).setTitle("ملف الزبون").setMessage(s.toString())
          .setPositiveButton("رجوع",null).show();
    }

    private void showPayments() {
        Cursor c=db.getReadableDatabase().rawQuery(
          "SELECT customers.name,payments.amount,payments.created FROM payments JOIN customers ON customers.id=payments.customer_id ORDER BY payments.id DESC",null);
        StringBuilder s=new StringBuilder();
        while(c.moveToNext()) s.append(c.getString(0)).append(" — ").append(money(c.getDouble(1)))
          .append(" DA\n").append(new Date(c.getLong(2))).append("\n\n");
        c.close();
        if(s.length()==0)s.append("لا توجد تسديدات مسجلة بعد.");
        new AlertDialog.Builder(this).setTitle("سجل التسديدات").setMessage(s.toString())
          .setPositiveButton("إغلاق",null).show();
    }

    private LinearLayout form(){ LinearLayout l=new LinearLayout(this); l.setOrientation(1); l.setPadding(dp(18),dp(8),dp(18),dp(4)); l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); return l; }
    private EditText field(String hint){ EditText e=new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(0xff8BABC5); e.setPadding(dp(10),dp(8),dp(10),dp(8)); e.setBackground(round(CARD,10)); LinearLayout.LayoutParams p=lp(-1,dp(52)); p.bottomMargin=dp(10); e.setLayoutParams(p); return e; }
    private Spinner spinner(List<Item> items){ ArrayAdapter<Item> a=new ArrayAdapter<Item>(this,android.R.layout.simple_spinner_dropdown_item,items); Spinner s=new Spinner(this); s.setAdapter(a); s.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); s.setBackground(round(CARD,8)); LinearLayout.LayoutParams p=lp(-1,dp(50)); p.bottomMargin=dp(10); s.setLayoutParams(p); return s; }
    private TextView text(String s,int size,int color,boolean bold){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); if(bold)t.setTypeface(null,Typeface.BOLD); t.setGravity(Gravity.CENTER_VERTICAL); t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); return t; }
    private android.graphics.drawable.GradientDrawable round(int color,int radius){ android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private String money(double v){return fmt.format(v);}
    private Double parse(String s){try{return Double.parseDouble(s.trim().replace(',','.'));}catch(Exception e){return null;}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    static class Item {
        long id; String name,phone;
        Item(long i,String n,String p){id=i;name=n;phone=p==null?"":p;}
        public String toString(){return name+(phone.isEmpty()?"":"  ("+phone+")");}
    }

    static class DB extends SQLiteOpenHelper {
        DB(Context c){super(c,"chafik_credit.db",null,1);}
        public void onCreate(SQLiteDatabase d){
            d.execSQL("CREATE TABLE customers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,phone TEXT,created INTEGER)");
            d.execSQL("CREATE TABLE debts(id INTEGER PRIMARY KEY AUTOINCREMENT,customer_id INTEGER NOT NULL,amount REAL NOT NULL,note TEXT,created INTEGER)");
            d.execSQL("CREATE TABLE payments(id INTEGER PRIMARY KEY AUTOINCREMENT,customer_id INTEGER NOT NULL,amount REAL NOT NULL,created INTEGER)");
        }
        public void onUpgrade(SQLiteDatabase d,int old,int now){}
        void exec(String sql,Object[] args){SQLiteDatabase d=getWritableDatabase(); android.database.sqlite.SQLiteStatement st=d.compileStatement(sql);
            for(int i=0;i<args.length;i++){Object x=args[i]; if(x instanceof Number)st.bindDouble(i+1,((Number)x).doubleValue()); else st.bindString(i+1,String.valueOf(x));}
            st.executeInsert(); st.close();}
        double sum(String sql){Cursor c=getReadableDatabase().rawQuery(sql,null); double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;}
        double customerSum(String table,long id){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount),0) FROM "+table+" WHERE customer_id=?",new String[]{""+id});double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;}
        double balance(long id){return customerSum("debts",id)-customerSum("payments",id);}
        List<Item> customers(){ArrayList<Item>a=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,name,phone FROM customers ORDER BY name",null);while(c.moveToNext())a.add(new Item(c.getLong(0),c.getString(1),c.getString(2)));c.close();return a;}
    }
}
