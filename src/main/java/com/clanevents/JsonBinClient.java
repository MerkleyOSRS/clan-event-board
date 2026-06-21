package com.clanevents;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.inject.Inject;
import java.io.IOException;
import java.util.function.Consumer;

@Slf4j
public class JsonBinClient
{
	private static final String BASE_URL = "https://api.jsonbin.io/v3/b/";
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	public void fetchEvents(String binId, String apiKey, Consumer<ClanEventsData> onSuccess, Runnable onFailure)
	{
		Request request = new Request.Builder()
			.url(BASE_URL + binId)
			.header("X-Master-Key", apiKey)
			.header("X-Bin-Meta", "false")
			.get()
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Failed to fetch events from JSONBin", e);
				onFailure.run();
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (response)
				{
					if (!response.isSuccessful())
					{
						log.debug("JSONBin fetch returned {}", response.code());
						onFailure.run();
						return;
					}
					ClanEventsData data = gson.fromJson(response.body().charStream(), ClanEventsData.class);
					if (data == null)
					{
						data = new ClanEventsData();
					}
					onSuccess.accept(data);
				}
			}
		});
	}

	public void saveEvents(String binId, String apiKey, ClanEventsData data, Runnable onFailure)
	{
		String json = gson.toJson(data);
		Request request = new Request.Builder()
			.url(BASE_URL + binId)
			.header("X-Master-Key", apiKey)
			.put(RequestBody.create(JSON, json))
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Failed to save events to JSONBin", e);
				onFailure.run();
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				response.close();
				if (!response.isSuccessful())
				{
					log.debug("JSONBin save returned {}", response.code());
					onFailure.run();
				}
			}
		});
	}
}
