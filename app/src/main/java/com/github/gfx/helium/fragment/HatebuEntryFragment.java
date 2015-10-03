package com.github.gfx.helium.fragment;

import com.google.android.gms.analytics.Tracker;

import com.cookpad.android.rxt4a.operators.OperatorAddToCompositeSubscription;
import com.cookpad.android.rxt4a.schedulers.AndroidSchedulers;
import com.cookpad.android.rxt4a.subscriptions.AndroidCompositeSubscription;
import com.github.gfx.helium.HeliumApplication;
import com.github.gfx.helium.R;
import com.github.gfx.helium.analytics.TrackingUtils;
import com.github.gfx.helium.api.HatebuFeedClient;
import com.github.gfx.helium.databinding.CardHatebuEntryBinding;
import com.github.gfx.helium.model.HatebuEntry;
import com.github.gfx.helium.widget.ArrayRecyclerAdapter;
import com.github.gfx.helium.widget.BindingHolder;
import com.github.gfx.helium.widget.LayoutManagers;
import com.github.gfx.helium.widget.OnItemClickListener;
import com.github.gfx.helium.widget.OnItemLongClickListener;

import org.joda.time.format.ISODateTimeFormat;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.Collections;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

@ParametersAreNonnullByDefault
public class HatebuEntryFragment extends Fragment implements OnItemClickListener, OnItemLongClickListener {

    static final String TAG = HatebuEntryFragment.class.getSimpleName();

    static final String kHatebuEntryPrefix = "http://b.hatena.ne.jp/entry/";

    static final String kCategory = "category";

    @Bind(R.id.list)
    RecyclerView listView;

    @Bind(R.id.swipe_refresh)
    SwipeRefreshLayout swipeRefreshLayout;

    @Inject
    HatebuFeedClient feedClient;

    @Inject
    Tracker tracker;

    EntriesAdapter adapter;

    final AndroidCompositeSubscription compositeSubscription = new AndroidCompositeSubscription();

    public HatebuEntryFragment() {
    }

    public static HatebuEntryFragment newInstance() {
        HatebuEntryFragment fragment = new HatebuEntryFragment();
        fragment.setArguments(new Bundle());
        return fragment;
    }

    public static HatebuEntryFragment newInstance(String category) {
        HatebuEntryFragment fragment = new HatebuEntryFragment();
        Bundle args = new Bundle();
        args.putString(kCategory, category);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        HeliumApplication.getAppComponent().inject(this);

        adapter = new EntriesAdapter(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_entry, container, false);
        ButterKnife.bind(this, view);

        adapter.setOnItemClickListener(this);
        adapter.setOnItemLongClickListener(this);

        listView.setAdapter(adapter);
        listView.setLayoutManager(LayoutManagers.create(getActivity()));

        swipeRefreshLayout.setColorSchemeResources(R.color.app_primary);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                reload().subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                });

            }
        });

        reload().subscribe();

        return view;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (isVisibleToUser) {
            String category = getCategory();
            TrackingUtils.sendScreenView(tracker, category != null ? TAG + "-" + category : TAG);
        }
    }

    Observable<?> reload() {
        Observable<List<HatebuEntry>> observable;
        if (getCategory() != null) {
            observable = feedClient.getHotentries(getCategory());
        } else {
            observable = feedClient.getHotentries();
        }
        return observable
                .observeOn(AndroidSchedulers.mainThread())
                .lift(new OperatorAddToCompositeSubscription<List<HatebuEntry>>(compositeSubscription))
                .doOnNext(new Action1<List<HatebuEntry>>() {
                    @Override
                    public void call(List<HatebuEntry> items) {
                        adapter.reset(items);
                    }
                }).onErrorReturn(new Func1<Throwable, List<HatebuEntry>>() {
                    @Override
                    public List<HatebuEntry> call(Throwable throwable) {
                        Log.w(TAG, "Error while loading entries", throwable);
                        if (getActivity() != null) {
                            Toast.makeText(getActivity(), "Error while loading entries\n"
                                            + throwable.getLocalizedMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                        return Collections.emptyList();
                    }
                });
    }

    @Nullable
    String getCategory() {
        return getArguments() != null ? getArguments().getString(kCategory) : null;
    }

    void openUri(String uri, String action) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        startActivity(intent);
        trackOpenUri(action);
    }

    @Override
    public void onItemClick(View view, int position) {
        HatebuEntry entry = adapter.getItem(position);
        openUri(entry.link, "original");
    }

    @Override
    public boolean onItemLongClick(View view, int position) {
        HatebuEntry entry = adapter.getItem(position);
        openUri(kHatebuEntryPrefix + entry.link, "service");
        return true;
    }

    void trackOpenUri(String action) {
        String category = getCategory();
        TrackingUtils
                .sendEvent(tracker, category != null ? TAG + "-" + category : TAG, action);
    }

    private class EntriesAdapter extends ArrayRecyclerAdapter<HatebuEntry, BindingHolder<CardHatebuEntryBinding>> {

        public EntriesAdapter(Context context) {
            super(context);
        }

        void reset(List<HatebuEntry> list) {
            clear();
            addAll(list);
            notifyDataSetChanged();
        }

        @Override
        public BindingHolder<CardHatebuEntryBinding> onCreateViewHolder(ViewGroup parent, int viewType) {
            return new BindingHolder<>(getContext(), parent, R.layout.card_hatebu_entry);
        }

        @Override
        public void onBindViewHolder(final BindingHolder<CardHatebuEntryBinding> holder, final int position) {
            CardHatebuEntryBinding binding = holder.binding;

            final HatebuEntry entry = getItem(position);

            holder.itemView.setClickable(true);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dispatchOnItemClick(v, position);
                }
            });
            holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return dispatchOnItemLongClick(v, position);
                }
            });


            binding.title.setText(entry.title);
            binding.date.setText(ISODateTimeFormat.date().print(entry.getTimestamp()));
            binding.subject.setText(TextUtils.join(" ", entry.subject));
            binding.bookmarkCount.setText(entry.bookmarkCount);
            binding.description.setText(entry.description);
            binding.originalUrl.setText(entry.link);

            binding.bookmarkCount.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openUri(kHatebuEntryPrefix + entry.link, "service");
                }
            });
        }
    }
}
