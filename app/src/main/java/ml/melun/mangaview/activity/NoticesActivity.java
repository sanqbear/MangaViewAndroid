package ml.melun.mangaview.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayout;
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import ml.melun.mangaview.Notice;
import ml.melun.mangaview.Preference;
import ml.melun.mangaview.R;
import okhttp3.Response;

import static ml.melun.mangaview.MainApplication.httpClient;
import static ml.melun.mangaview.Utils.showPopup;

public class NoticesActivity extends AppCompatActivity {
    Boolean dark;
    Context context;
    List<Notice> notices;
    SharedPreferences sharedPref;
    ListView list;
    SwipyRefreshLayout swipe;
    ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (dark = new Preference(this).getDarkTheme())
            setTheme(R.style.AppThemeDark);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notices);
        ActionBar actionBar = getSupportActionBar();
        notices = new ArrayList<>();
        sharedPref = this.getSharedPreferences("mangaView", Context.MODE_PRIVATE);
        context = this;
        swipe = this.findViewById(R.id.noticeSwipe);
        list = this.findViewById(R.id.noticeList);
        progress = this.findViewById(R.id.progress);
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(true);
        // reset old notices sharedpref
        sharedPref.edit().putString("notices", "").commit();
        notices = new Gson().fromJson(sharedPref.getString("notice", "[]"), new TypeToken<List<Notice>>() {
        }.getType());
        // check notices for null object
        for (int i = notices.size() - 1; i >= 0; i--) {
            if (notices.get(i) == null)
                notices.remove(i);
        }
        progress.setVisibility(View.VISIBLE);
        swipe.setOnRefreshListener(direction -> {
            getNotices gn = new getNotices();
            gn.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        });
        getNotices gn = new getNotices();
        gn.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    void showNotice(Notice notice) {
        showPopup(context, notice.getTitle(), notice.getDate() + "\n\n" + notice.getContent());
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    void populate() {
        // save notice titles to array
        try {
            String[] list = new String[notices.size()];
            for (int i = 0; i < notices.size(); i++) {
                String title = notices.get(i).getTitle();
                if (title == null)
                    list[i] = "";
                else
                    list[i] = notices.get(i).getId() + ". " + title;
            }
            final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list);
            // populate listview and set click listener
            this.list.setAdapter(adapter);
            this.list.setOnItemClickListener((adapterView, view, position, id) -> {
                Notice target = notices.get(position);
                showNotice(target);
            });
        } catch (Exception e) {
            showPopup(context, "오류", e.getMessage());
            e.printStackTrace();
        }
        // create arrayAdapter

    }

    private class getNotices extends AsyncTask<Void, Void, Integer> {
        List<Notice> loaded;

        protected void onPreExecute() {
            super.onPreExecute();
            sharedPref.edit().putLong("lastNoticeTime", System.currentTimeMillis()).commit();
        }

        protected Integer doInBackground(Void... params) {
            // mget all notices
            try {
                Response response = httpClient.get(
                        "https://raw.githubusercontent.com/junheah/MangaViewAndroid/master/etc/notices.json",
                        new HashMap<>());
                String rawdata = response.body().string();
                response.close();
                loaded = new Gson().fromJson(rawdata, new TypeToken<List<Notice>>() {
                }.getType());
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);
            try {
                for (Notice n : loaded) {
                    if (n != null) {
                        int index = notices.indexOf(n);
                        if (index > -1)
                            notices.set(index, n);
                        else
                            notices.add(n);
                    }
                }
                Collections.sort(notices, (n1, n2) -> {
                    if (n1.getId() < n2.getId())
                        return 1;
                    else
                        return -1;
                });
            } catch (Exception e) {
                // probably offline
            }
            sharedPref.edit().putString("notice", new Gson().toJson(notices)).commit();
            swipe.setRefreshing(false);
            progress.setVisibility(View.GONE);
            populate();
        }
    }
}
