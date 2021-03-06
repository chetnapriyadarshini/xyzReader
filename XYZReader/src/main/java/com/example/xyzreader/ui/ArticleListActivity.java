package com.example.xyzreader.ui;

import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.SharedElementCallback;
import android.support.v4.util.Pair;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.example.xyzreader.data.UpdaterService;

import java.util.List;
import java.util.Map;

/**
 * An activity representing a list of Articles. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a {@link ArticleDetailActivity} representing item details. On tablets, the
 * activity presents a grid of items as cards.
 */
public class ArticleListActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = ArticleListActivity.class.getSimpleName();
    public static final String EXTRA_STARTING_ARTICLE_POSITION = "extra_starting_article_position";
    public static final String EXTRA_CURRENT_ARTICLE_POSITION = "extra_current_article_position";
    private Toolbar mToolbar;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;
    private Bundle mTempReenterTransition;

    private SharedElementCallback sharedElementCallback = new SharedElementCallback() {

        @Override
        public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
            super.onMapSharedElements(names, sharedElements);
            Log.d(TAG, "SHARED ELEMENT CALLBACK "+mTempReenterTransition);
            if(mTempReenterTransition != null){
                int startingposition = mTempReenterTransition.getInt(EXTRA_STARTING_ARTICLE_POSITION);
                int currentposition = mTempReenterTransition.getInt(EXTRA_CURRENT_ARTICLE_POSITION);
                if(startingposition != currentposition){
                    //user changed the page he originally entered update shared element
                    //so that the transition takes place on appropiriate element
                    String newTransitionName = Constants.article_name[currentposition];
                    View newSharedElement = mRecyclerView.findViewWithTag(Constants.article_name[currentposition]);
                    names.clear();
                    names.add(newTransitionName);
                    sharedElements.clear();
                    sharedElements.put(newTransitionName, newSharedElement);
                    Log.d(TAG, "New transition name: "+newTransitionName+newSharedElement);
                    if(mRecyclerView == null)
                        Log.d(TAG , "Recyler View nulllllllll");

                }
                mTempReenterTransition = null;
            }else {
/*
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    View navigationBar = findViewById(android.R.id.navigationBarBackground);
                    View statusBar = findViewById(android.R.id.statusBarBackground);
                    if (navigationBar != null) {
                        names.add(navigationBar.getTransitionName());
                        sharedElements.put(navigationBar.getTransitionName(), navigationBar);
                    }
                    if (statusBar != null) {
                        names.add(statusBar.getTransitionName());
                        sharedElements.put(statusBar.getTransitionName(), statusBar);
                    }
                }*/
            }
        }
    };
    private boolean isTransitionPending = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_list);
        setExitSharedElementCallback(sharedElementCallback);
        mToolbar = (Toolbar) findViewById(R.id.toolbar);


        final View toolbarContainerView = findViewById(R.id.toolbar_container);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        getLoaderManager().initLoader(0, null, this);

        if (savedInstanceState == null) {
            refresh();
        }
    }

    @Override
    public void onActivityReenter(int resultCode, Intent data) {
        super.onActivityReenter(resultCode, data);
        mTempReenterTransition = new Bundle(data.getExtras());
        int startingposition = mTempReenterTransition.getInt(EXTRA_STARTING_ARTICLE_POSITION);
        final int currentposition = mTempReenterTransition.getInt(EXTRA_CURRENT_ARTICLE_POSITION);
        if(startingposition != currentposition) {
            //call notify data set changed to ensure the new view is bound to the recycler view
            //this addresses situations when we are returning from the view after page change
            //this view was not visible in the initial window as we did not scroll down
            mRecyclerView.getAdapter().notifyItemChanged(currentposition);
            mRecyclerView.smoothScrollToPosition(currentposition);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            postponeEnterTransition();
            Log.d(TAG, "POSTPONEEEEEEEEEEE");
        }

        mRecyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this);
                mRecyclerView.requestLayout();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    //we make sure that the view we are trying to return transition to is bound
                    //If it is not we wait for it to be attached to the window
                    //else we start the transition
                    if (mRecyclerView.findViewHolderForAdapterPosition(currentposition) == null)
                        isTransitionPending = true;
                    else {
                        startPostponedEnterTransition();
                    }
                }
                return true;
            }
        });
    }

    private void refresh() {
        startService(new Intent(this, UpdaterService.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    private boolean mIsRefreshing = false;

    private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
                updateRefreshingUI();
            }
        }
    };

    private void updateRefreshingUI() {
        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        Adapter adapter = new Adapter(cursor);
        adapter.setHasStableIds(true);

        int columnCount = getResources().getInteger(R.integer.list_column_count);
        StaggeredGridLayoutManager sglm =
                new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(sglm);
        mRecyclerView.setAdapter(adapter);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mRecyclerView.setAdapter(null);
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor mCursor;

        public Adapter(Cursor cursor) {
            mCursor = cursor;
        }

        @Override
        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getLong(ArticleLoader.Query._ID);
        }

        @Override
        public ViewHolder onCreateViewHolder(final ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);
            final ViewHolder vh = new ViewHolder(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //Add transition animation on activity exit
                    Bundle bundle = null;
                //    Log.d(TAG, "TRANSITION NAME IN MAIN ACTIVITY "+vh.thumbnailView.getTransitionName());

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP)
                    {
                        bundle = ActivityOptionsCompat.
                                makeSceneTransitionAnimation(ArticleListActivity.this,
                                       new Pair<View, String>(vh.thumbnailView,
                                               vh.thumbnailView.getTransitionName())).toBundle();
                    }
               //     Log.d(TAG, "TRANSITION NAME FIRST ACTIVITY: "+transitionName);
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            ItemsContract.Items.buildItemUri(getItemId(vh.getAdapterPosition())));
                    intent.putExtra(EXTRA_STARTING_ARTICLE_POSITION, vh.getAdapterPosition());
                    startActivity(intent,bundle);
                }
            });
            return vh;
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            //Log.d(TAG, "View holder bind for "+position+" position");
            mCursor.moveToPosition(position);
            holder.titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            holder.subtitleView.setText(
                    DateUtils.getRelativeTimeSpanString(
                            mCursor.getLong(ArticleLoader.Query.PUBLISHED_DATE),
                            System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_ALL).toString()
                            + " by "
                            + mCursor.getString(ArticleLoader.Query.AUTHOR));
            ImageLoader imageLoader =
                    ImageLoaderHelper.getInstance(ArticleListActivity.this).getImageLoader();
            holder.thumbnailView.setImageUrl(
                    mCursor.getString(ArticleLoader.Query.THUMB_URL),imageLoader);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                holder.thumbnailView.setTransitionName(Constants.article_name[position]);
            }
            holder.thumbnailView.setTag(Constants.article_name[position]);
            imageLoader.get(mCursor.getString(ArticleLoader.Query.THUMB_URL), new ImageLoader.ImageListener() {
                        @Override
                        public void onResponse(ImageLoader.ImageContainer imageContainer, boolean b) {
                            Bitmap bitmap = imageContainer.getBitmap();
                            if (bitmap != null) {
                                 Palette p = Palette.generate(bitmap, 12);
                                 int darkMutedColor = p.getDarkMutedColor(0xFF333333);
                                int lightMutedcolor =  p.getLightMutedColor(Color.WHITE);
                                holder.titleView.setBackgroundColor(darkMutedColor);
                                holder.titleView.setTextColor(lightMutedcolor);
                                holder.subtitleView.setBackgroundColor(darkMutedColor);
                                holder.subtitleView.setTextColor(lightMutedcolor);
                            }
                        }

                        @Override
                        public void onErrorResponse(VolleyError volleyError) {

                        }
                    });
            holder.thumbnailView.setAspectRatio(mCursor.getFloat(ArticleLoader.Query.ASPECT_RATIO));

        }

        @Override
        public int getItemCount() {
            return mCursor.getCount();
        }

        @Override
        public void onViewAttachedToWindow(ViewHolder holder) {
            super.onViewAttachedToWindow(holder);
            int pos = holder.getAdapterPosition();
            //Now that the view has been attached to the window, we check if it has any pending
            //transitions, if yes we proceed with the transition
           // Log.d(TAG, "View at position "+pos+" attached to window");
            if(isTransitionPending && mTempReenterTransition!= null
                    && mTempReenterTransition.getInt(EXTRA_CURRENT_ARTICLE_POSITION) == pos){
              //  Log.d(TAG, "Selected View at "+pos+"position has been attached, we can proceed with the transition");
                isTransitionPending = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mRecyclerView.smoothScrollToPosition(pos);
                    startPostponedEnterTransition();
                //    Log.d(TAG, "STARTTTTTTTTTTTT");
                }
            }
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public DynamicHeightNetworkImageView thumbnailView;
        public TextView titleView;
        public TextView subtitleView;

        public ViewHolder(View view) {
            super(view);
            thumbnailView = (DynamicHeightNetworkImageView) view.findViewById(R.id.thumbnail);
            titleView = (TextView) view.findViewById(R.id.article_title);
            subtitleView = (TextView) view.findViewById(R.id.article_subtitle);
        }
    }
}
