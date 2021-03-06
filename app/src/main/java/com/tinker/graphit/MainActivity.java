package com.tinker.graphit;

import android.Manifest;
import android.accounts.Account;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.ndk.CrashlyticsNdk;
import io.fabric.sdk.android.Fabric;

import java.lang.annotation.Target;
import java.util.ArrayList;

/**
 * Created by sonny.kurniawan on 2015/10/11.
 */
public class MainActivity extends Activity implements ChartDataInputDialogFragment.OnDialogFragmentClickListener{
    private AccountSelector account_selector_instance;
    private EditText data_row_number, data_column_number, axis_column_number;
    private String tableName, sheetName, data_row_number_string, data_column_number_string, axis_column_number_string;
    private Spinner account_selector_spinner;
    private ArrayList<Account> accounts_in_device;

    private static final String DEFAULT_CALLER = "FAB_BUTTON";
    private static final String EDIT_LIST = "EDITING";

    //Temporary variable to hold the list of charts.
    private ArrayList<TargetChartInfo> current_viewed_charts;

    private static final int REQUEST_GET_ACCOUNT = 112;
    public static final String DEFAULT_ENTRY = "default_entry";

    public ChartListRecyclerAdapter recyclerAdapter;
    private RecyclerView recyclerView;
    private TextView empty_textview;
    private LinearLayoutManager layoutManager;
    private ChartDataInputDialogFragment generalDialogFragment;

    private ChartDatabaseOperator database_operator;
    private Cursor chartCursor;

    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Fabric.with(this, new Crashlytics(), new CrashlyticsNdk());
        setContentView(R.layout.activity_main);

        initialize();
        current_viewed_charts = new ArrayList<>();
        ArrayList<TargetChartInfo> list = new ArrayList<>();

        //Create or access database
        database_operator = new ChartDatabaseOperator(this);
            database_operator.openChartDatabase();
        chartCursor = database_operator.fetchChartData();

        if (chartCursor.moveToFirst()){

            do{
                Log.v("chartCursor", "found chart data");
                String data_id = chartCursor.getString(chartCursor.getColumnIndex("Data_Id"));
                String account_name = chartCursor.getString(chartCursor.getColumnIndex("Account_Name"));
                String chart_name = chartCursor.getString(chartCursor.getColumnIndex("Chart_Name"));
                String sheet_name = chartCursor.getString(chartCursor.getColumnIndex("Sheet_Name"));
                String starting_row = chartCursor.getString(chartCursor.getColumnIndex("Starting_Row_Number"));
                String data_col = chartCursor.getString(chartCursor.getColumnIndex("Data_Col_Number"));
                String axis_col = chartCursor.getString(chartCursor.getColumnIndex("Axis_Col_Number"));

                Account account_to_check = checkAccountNameExistsInDevice(account_name);
                if(account_to_check != null){
                    TargetChartInfo this_chart_info = new TargetChartInfo();
                    this_chart_info.setUserAccount(account_to_check);
                    this_chart_info.setTableName(chart_name);
                    this_chart_info.setSheetName(sheet_name);
                    this_chart_info.setDataRowNumber(starting_row);
                    this_chart_info.setDataColumnNumber(data_col);
                    this_chart_info.setAxisColumnNumber(axis_col);
                    list.add(this_chart_info);
                    current_viewed_charts.add(this_chart_info);
                }else{
                    Log.v("account not found", account_name);
            }

            }while(chartCursor.moveToNext());
        }
        chartCursor.close();

        if(android.os.Build.VERSION.SDK_INT > 22){
            if(isGETACCOUNTSAllowed()){
                //initialize();
                //return;
            }else{
                requestGET_ACCOUNTSPermission();
            }
        }else{
            //initialize();
        }


        recyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);
        empty_textview = (TextView) findViewById(R.id.empty_view);

        layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);

        recyclerView.setLayoutManager(layoutManager);
        Log.v("chart list size", Integer.toString(list.size()));
        recyclerAdapter = new ChartListRecyclerAdapter(list);
        recyclerView.setAdapter(recyclerAdapter);

        ImageButton fabButton = (ImageButton) findViewById(R.id.add_chart_button);
        fabButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInputDialogForChart(DEFAULT_CALLER, -1);
            }
        });

        ImageButton helpButton = (ImageButton) findViewById(R.id.show_manual_button);
        helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showManual();
            }
        });

        ImageButton privacypolicyButton = (ImageButton) findViewById(R.id.show_privacy_policy_button);
        privacypolicyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPrivacyPolicy();
            }
        });
    }

    private Account checkAccountNameExistsInDevice(String account_name){
        Log.v("checking account found", account_name);
        for(Account this_account : accounts_in_device){
            Log.v("account found", this_account.name);

            if(this_account.name.equals(account_name)){
                Log.v("account check result", "true");
                return this_account;

            }else{return null;}
        }
        return null;
    }

    public void showInputDialogForChart(String caller, int position){
        generalDialogFragment =
                ChartDataInputDialogFragment.newInstance("title", "message", accounts_in_device, this, caller, position);
        generalDialogFragment.show(getFragmentManager(),"dialog");

    }

    @Override
    public void showChartClicked(ChartDataInputDialogFragment dialog, View view, String caller, int position){
        showChart(view, caller, position);
    }

    public void editListItem(int position){
        showInputDialogForChart(EDIT_LIST, position);
    }

    public void rearrangeDeletedChartList(int position){
        current_viewed_charts.remove(position);
    }

    private void initialize(){
        account_selector_instance = new AccountSelector();
        accounts_in_device = account_selector_instance.initAccountSelector(this);

    }


    protected void onRestart(){
        super.onRestart();
        //Todo: sync database contents and current_viewed_charts

    }

    protected void onPause(){
        TargetChartInfo chart_info;
        super.onPause();
        database_operator.deleteAllRecords();
        for(int i=0; i<current_viewed_charts.size(); i++) {
            Log.v("chart db","inserted" + i + " times");
            chart_info = current_viewed_charts.get(i);
            database_operator.insertNewChartToDatabase(chart_info.getUserAccount().name, chart_info.getTableName(), chart_info.getSheetName(), chart_info.getRowWhereDataStarts(), chart_info.getDataColumnNumber(), chart_info.getAxisColumnNumber());
        }
    }

    protected void onResume(){
        super.onResume();
        recyclerAdapter.setOnItemClickListener(new ChartListRecyclerAdapter
                .MyClickListener() {
            @Override
            public void onItemClick(int position, View v) {
                Log.i("onResume", " Clicked on Item " + position);
                showChartFromList(position);

            }
        });

        recyclerAdapter.setEditItemClickListener(new ChartListRecyclerAdapter.ChartListClickListener() {
            @Override
            public void editChartItem(int position) {
                editListItem(position);
            }
        });

        recyclerAdapter.syncListPositions(new ChartListRecyclerAdapter.ChartListSynchronizer() {
            @Override
            public void syncDeletedPosition(int position) {
                rearrangeDeletedChartList(position);
            }
        });


        if(current_viewed_charts.size() == 0){
            empty_textview.setVisibility(View.VISIBLE);
        }else{
            empty_textview.setVisibility(View.GONE);
        }

        if(account_selector_instance != null) {
            account_selector_instance.initTableParameter();
        }
    }

    private boolean getUserInputOfTableInformation(View view){

        account_selector_spinner = (Spinner) view.findViewById(R.id.account_selector);
        data_row_number = (EditText) view.findViewById(R.id.data_row_number_input_field);
        axis_column_number = (EditText) view.findViewById(R.id.axis_col_number_input_field);
        data_column_number = (EditText) view.findViewById(R.id.data_col_number_input_field);
        tableName = ((EditText) view.findViewById(R.id.table_name_input_field)).getText().toString();
        sheetName = ((EditText) view.findViewById(R.id.sheet_name_input_field)).getText().toString();
        if(TextUtils.isEmpty(data_row_number.getText())){
            data_row_number_string = account_selector_instance.DEFAULT_ENTRY;
            return false;
        }else{
            data_row_number_string =data_row_number.getText().toString();
        }
        if(TextUtils.isEmpty(data_column_number.getText())){
            data_column_number_string = account_selector_instance.DEFAULT_ENTRY;
            return false;
        }else{
            data_column_number_string = data_column_number.getText().toString();
        }
        if(TextUtils.isEmpty(data_column_number.getText())){
            axis_column_number_string = account_selector_instance.DEFAULT_ENTRY;
            return false;
        }else{
            axis_column_number_string = axis_column_number.getText().toString();
        }
        account_selector_instance.table_to_reference.setUserAccount(accounts_in_device.get(account_selector_spinner.getSelectedItemPosition()));
        account_selector_instance.table_to_reference.setTableName(tableName);
        account_selector_instance.table_to_reference.setSheetName(sheetName);
        account_selector_instance.table_to_reference.setAxisColumnNumber(axis_column_number_string);
        account_selector_instance.table_to_reference.setDataColumnNumber(data_column_number_string);
        account_selector_instance.table_to_reference.setDataRowNumber(data_row_number_string);
        return true;
    }

    private void showManual(){
        Intent manualIntent = new Intent(MainActivity.this, WebViewActivity.class);
        manualIntent.putExtra("url", "https://ancient-harbor-5436.herokuapp.com/howto_graphit");
        this.startActivity(manualIntent);
    }
    private void showPrivacyPolicy(){
        Intent manualIntent = new Intent(MainActivity.this, WebViewActivity.class);
        manualIntent.putExtra("url", "https://ancient-harbor-5436.herokuapp.com/app_privacy_policy");
        this.startActivity(manualIntent);
    }

    private void modifyChartistItem(int position, TargetChartInfo new_chart_info){
        current_viewed_charts.set(position, new_chart_info);
    }

    private void showChartFromList(int position){
        Intent myIntent = new Intent(MainActivity.this, GraphActivity.class);
        myIntent.putExtra("account_selected", current_viewed_charts.get(position));
        this.startActivity(myIntent);
    }

    private void showChart(View view, String caller, int position){

        if(!getUserInputOfTableInformation(view)){
            Toast.makeText(this,R.string.table_info_incomplete,Toast.LENGTH_LONG).show();
            generalDialogFragment.dismiss();
            return;
        }


        generalDialogFragment.dismiss();
        if(account_selector_instance.checkTableParameter() && caller == DEFAULT_CALLER && position==-1){
            current_viewed_charts.add(account_selector_instance.table_to_reference);
            recyclerAdapter.addItem(account_selector_instance.table_to_reference, recyclerAdapter.getItemCount());
            recyclerAdapter.notifyItemChanged(recyclerAdapter.getItemCount());
            Intent myIntent = new Intent(MainActivity.this, GraphActivity.class);
            myIntent.putExtra("account_selected", account_selector_instance.table_to_reference);
            this.startActivity(myIntent);
        }else{
            modifyChartistItem(position, account_selector_instance.table_to_reference);
        }
    }

    private boolean isGETACCOUNTSAllowed() {
        //Getting the permission status
        int result = ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS);

        //If permission is granted returning true
        if (result == PackageManager.PERMISSION_GRANTED)
            return true;

        //If permission is not granted returning false
        return false;
    }


    //if you don't have the permission then Requesting for permission
    private void requestGET_ACCOUNTSPermission(){

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.GET_ACCOUNTS)){


        }
        //And finally ask for the permission
        ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.GET_ACCOUNTS},REQUEST_GET_ACCOUNT);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        //Checking the request code of our request
        if(requestCode == REQUEST_GET_ACCOUNT){
            if(grantResults.length >0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this,"Thanks You For Permission Granted ",Toast.LENGTH_LONG).show();
                initialize();
            }else{
                Toast.makeText(this,"Oops you just denied the permission", Toast.LENGTH_LONG).show();
            }
        }

    }

}