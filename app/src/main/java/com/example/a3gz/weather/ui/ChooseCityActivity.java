package com.example.a3gz.weather.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.example.a3gz.weather.City;
import com.example.a3gz.weather.R;
import com.example.a3gz.weather.db.WeatherDB;
import com.example.a3gz.weather.utils.HttpCallback;
import com.example.a3gz.weather.utils.HttpUtil;
import com.example.a3gz.weather.utils.Utility;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by 3gz on 2016/10/18.
 */

public class ChooseCityActivity extends Activity {

    private WeatherDB weatherDB;//数据库操作对象
    private ProgressDialog mProgressDialog;//进度条对话框
    private EditText editText;//搜索编辑框
    private ArrayAdapter<String> mAdapter;//ListView适配器
    private ListView mListView;//城市ListView
    private List<String> cityNames = new ArrayList<>();//用于存放与输入的内容相匹配的城市名称字符串
    private City mCity_selected;//选中的城市
    private List<City> mCities;//用于存放与输入的内容相匹配的城市名称对象

    private static final int NONE_DATA = 0;//标识是否有初始化城市数据

    private SharedPreferences mSharedPreferences;//本地存储
    private SharedPreferences.Editor mEditor;//本地存储

    protected static final String ACTIVITY_TAG="choosecityrequest";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.choose_city);

        weatherDB = WeatherDB.getInstance(this);//获取数据库处理对象
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);//获取本地存储对象
        mEditor = mSharedPreferences.edit();//获取本地存储对象

        //先检查本地是否已同步过城市数据，如果没有，则从服务器同步
        if (weatherDB.checkDataState() == NONE_DATA) {
            queryCitiesFromServer();
        }

        mCities = queryCitiesFromLocal("");//获取本地存储的所有的城市

        //搜索框，设置文本变化监听器
        editText = (EditText) findViewById(R.id.edit_city);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mCities = queryCitiesFromLocal(s.toString());//每次文本变化就去本地数据库查询匹配的城市
                mAdapter.notifyDataSetChanged();//通知更新
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        mAdapter = new ArrayAdapter<>(this,R.layout.appearence, cityNames);//适配器初始化
        mListView = (ListView) findViewById(R.id.list_view_cities);
        mListView.setAdapter(mAdapter);

        //ListView的Item点击事件
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mCity_selected = mCities.get(position);//根据点击的位置获取对应的City对象
                showProgressDialog();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        queryWeatherFromServer();//根据点击的城市从服务器获取天气数据
                    }
                }).start();

            }
        });
    }

    //从服务器取出所有的城市信息
    private void queryCitiesFromServer() {
        String address = " https://api.heweather.com/x3/citylist?search=allchina&key="
                + WeatherActivity.WEATHER_KEY;
        showProgressDialog();

        HttpUtil.sendHttpRequest(address, new HttpCallback() {
            @Override
            public void onFinish(String response) {
                if (Utility.handleCityResponse(weatherDB, response)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            weatherDB.updateDataState();
                        }
                    });
                }
            }
            @Override
            public void onError(final Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(ChooseCityActivity.this, e.getMessage(),
                                Toast.LENGTH_SHORT).show();//把异常Toast显示出来
                    }
                });
            }
        });
    }

    //从本地数据库取出相似的城市名称
    private List<City> queryCitiesFromLocal(String name) {
        List<City> cities = weatherDB.loadCitiesByName(name);
        cityNames.clear();
        for (City city : cities) {
            cityNames.add(city.getCity_name_ch());
        }
        return cities;
    }

    //从服务器获取天气数据
    private void queryWeatherFromServer() {


        String address = "https://api.heweather.com/x3/weather?cityid=" + mCity_selected.getCity_code() + "&key=" + WeatherActivity.WEATHER_KEY;
        Log.v(ACTIVITY_TAG,"address==========="+address);


        HttpUtil.sendHttpRequest(address, new HttpCallback() {
            @Override
            public void onFinish(String response) {
                //将从服务器获取的JSON数据进行解析
                if (Utility.handleWeatherResponse(mEditor, response)) {
                    //对线程的处理
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            //处理完天气数据，说明已经保存到本地，不用再把数据封装到Intent里面返回给WeatherActivity
                            //可以在onActivityResult里面从本地存储中获取
                            setResult(RESULT_OK);
                            finish();
                        }
                    });
                }
            }

            @Override
            public void onError(Exception e) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(ChooseCityActivity.this, "数据同步失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    //显示进度条
    private void showProgressDialog() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setMessage("loading...");
            mProgressDialog.setCanceledOnTouchOutside(false);
        }
        mProgressDialog.show();
    }

    //关闭进度条
    private void closeProgressDialog() {

        if (mProgressDialog != null)
            mProgressDialog.dismiss();
    }
}
