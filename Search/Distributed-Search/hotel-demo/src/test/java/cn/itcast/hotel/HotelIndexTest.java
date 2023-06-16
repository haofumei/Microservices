package cn.itcast.hotel;

import cn.itcast.hotel.pojo.Hotel;
import cn.itcast.hotel.pojo.HotelDoc;
import cn.itcast.hotel.service.IHotelService;
import com.alibaba.fastjson.JSON;
import org.apache.http.HttpHost;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.elasticsearch.xcontent.XContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static cn.itcast.hotel.constants.HotelConstants.MAPPING_HOTEL;

@SpringBootTest
public class HotelIndexTest {

    @Autowired
    private IHotelService hotelService;

    private RestHighLevelClient client;

    @Test
    void testMatchAll() throws IOException {
        SearchRequest request = new SearchRequest("hotel");

        request.source().query(QueryBuilders.matchAllQuery());

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        handleResponse(response);
    }

    private static void handleResponse(SearchResponse response) {
        SearchHits hits = response.getHits();
        long total = hits.getTotalHits().value;
        System.out.println("get total search: " + total + " documents");

        SearchHit[] docs = hits.getHits();

        for (SearchHit doc : docs) {
            String json = doc.getSourceAsString();
            Hotel hotel = JSON.parseObject(json, Hotel.class);
            Map<String, HighlightField> highlightFields = doc.getHighlightFields();

            if (!CollectionUtils.isEmpty(highlightFields)) {
                HighlightField highlightField = highlightFields.get("name");
                if (highlightField != null) {
                    String name = highlightField.getFragments()[0].string();
                    hotel.setName(name);
                }
                System.out.println(hotel);
            }
        }
    }

    @Test
    void testMatch() throws IOException {
        SearchRequest request = new SearchRequest("hotel");

        request.source().query(QueryBuilders.matchQuery("all", "如家"));

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        handleResponse(response);
    }

    @Test
    void testBool() throws IOException {
        SearchRequest request = new SearchRequest("hotel");

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        boolQuery.must(QueryBuilders.termQuery("brand", "如家"));

        boolQuery.filter(QueryBuilders.rangeQuery("price").lte(400));

        request.source().query(boolQuery);

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        handleResponse(response);
    }

    @Test
    void initiateES() throws IOException {
        List<Hotel> hotels = hotelService.list();

        BulkRequest bulkRequest = new BulkRequest();

        for (Hotel hotel : hotels) {
            HotelDoc hotelDoc = new HotelDoc(hotel);

            bulkRequest.add(new IndexRequest("hotel")
                    .id(hotelDoc.getId().toString())
                    .source(JSON.toJSONString(hotelDoc), XContentType.JSON));

        }

        client.bulk(bulkRequest, RequestOptions.DEFAULT);
    }

    @Test
    void testSuggest() throws IOException {
        SearchRequest request = new SearchRequest("hotel");

        request.source().suggest(new SuggestBuilder().addSuggestion(
                "suggestion",
                SuggestBuilders.completionSuggestion("suggestion")
                        .prefix("h")
                        .skipDuplicates(true)
                        .size(10)
        ));

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        Suggest suggest = response.getSuggest();

        CompletionSuggestion suggestions = suggest.getSuggestion("suggestion");

        List<CompletionSuggestion.Entry.Option> options = suggestions.getOptions();

        for (CompletionSuggestion.Entry.Option option : options) {
            String text = option.getText().toString();
            System.out.println(text);
        }
    }

    @BeforeEach
    void setUp() {
        this.client = new RestHighLevelClient(RestClient.builder(
                HttpHost.create("localhost:9200")
        ));
    }

    @AfterEach
    void close() throws IOException {
        this.client.close();
    }
}
