package com.github.gfx.helium.api;

import com.google.gson.Gson;

import com.github.gfx.helium.model.EpitomeBeam;
import com.github.gfx.helium.model.EpitomeEntry;
import com.squareup.okhttp.OkHttpClient;

import java.util.List;

import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.GsonConverter;
import retrofit.http.GET;
import rx.Observable;
import rx.functions.Func1;

public class EpitomeFeedClient {
    final String ENDPOINT = "https://ja.epitomeup.com/";

    final RestAdapter adapter;

    final EpitomeService service;

    public EpitomeFeedClient(OkHttpClient httpClient) {
        adapter = new RestAdapter.Builder()
                .setClient(new OkClient(httpClient))
                .setEndpoint(ENDPOINT)
                .setConverter(new GsonConverter(new Gson()))
                .build();

        service = adapter.create(EpitomeService.class);
    }

    public Observable<List<EpitomeEntry>> getEntries() {
        return service.getBeam().map(new Func1<EpitomeBeam, List<EpitomeEntry>>() {
            @Override
            public List<EpitomeEntry> call(EpitomeBeam epitomeBeam) {
                return epitomeBeam.sources;
            }
        });
    }

    static interface EpitomeService {
        @GET("/feed/beam")
        Observable<EpitomeBeam> getBeam();
    }
}
