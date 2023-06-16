package cn.itcast.hotel.service.impl;

import cn.itcast.hotel.mapper.HotelMapper;
import cn.itcast.hotel.pojo.Hotel;
import cn.itcast.hotel.pojo.HotelDoc;
import cn.itcast.hotel.pojo.PageResult;
import cn.itcast.hotel.pojo.RequestParams;
import cn.itcast.hotel.service.IHotelService;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.logging.log4j.util.StringBuilders;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HotelService extends ServiceImpl<HotelMapper, Hotel> implements IHotelService {

    @Autowired
    private RestHighLevelClient client;

    @Override
    public void insertById(Long id) {
        try {
            Hotel hotel = getById(id);

            HotelDoc hotelDoc = new HotelDoc(hotel);

            IndexRequest request = new IndexRequest("hotel").id(id.toString());

            request.source(JSON.toJSONString(hotelDoc), XContentType.JSON);

            client.index(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteById(Long id) {
        try {
            DeleteRequest request = new DeleteRequest("hotel").id(id.toString());
            client.delete(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> getSuggestion(String key) {
        try {
            SearchRequest request = new SearchRequest("hotel");

            request.source().suggest(new SuggestBuilder().addSuggestion(
                    "suggestion",
                    SuggestBuilders.completionSuggestion("suggestion")
                            .prefix(key)
                            .skipDuplicates(true)
                            .size(10)
            ));

            SearchResponse response = client.search(request, RequestOptions.DEFAULT);

            Suggest suggest = response.getSuggest();

            CompletionSuggestion suggestions = suggest.getSuggestion("suggestion");

            List<CompletionSuggestion.Entry.Option> options = suggestions.getOptions();

            List<String> result = new ArrayList<>();

            for (CompletionSuggestion.Entry.Option option : options) {
                String text = option.getText().toString();
                result.add(text);
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PageResult search(RequestParams requestParams) {

        try {
            SearchRequest request = new SearchRequest("hotel");

            // 1. query
            buildBasicQuery(requestParams, request);
            // 2. paginate
            int page = requestParams.getPage();
            int size = requestParams.getSize();
            request.source().from((page - 1) * size).size(size);
            // 3. sort
            String location = requestParams.getLocation();
            if (location != null && !location.equals("")) {
                request.source().sort(SortBuilders
                        .geoDistanceSort("location", new GeoPoint(location))
                        .order(SortOrder.DESC)
                        .unit(DistanceUnit.KILOMETERS));
            }

            SearchResponse response = client.search(request, RequestOptions.DEFAULT);

            return handleResponse(response);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, List<String>> filter(RequestParams params) {
        try {
            SearchRequest request = new SearchRequest("hotel");

            buildBasicQuery(params, request);

            request.source().size(0);

            buildAggregation(request);

            SearchResponse response = client.search(request, RequestOptions.DEFAULT);

            Map<String, List<String>> result = new HashMap<>();
            Aggregations aggregations = response.getAggregations();

            List<String> brandList = getAggByName(aggregations, "brandAgg");
            result.put("brand", brandList);

            List<String> cityList = getAggByName(aggregations, "cityAgg");
            result.put("city", cityList);

            List<String> starList = getAggByName(aggregations, "starNameAgg");
            result.put("starName", starList);

            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> getAggByName(Aggregations aggregations, String aggName) {
        Terms brandTerms = aggregations.get(aggName);

        List<? extends Terms.Bucket> buckets = brandTerms.getBuckets();
        List<String> brandList = new ArrayList<>();

        for (Terms.Bucket bucket : buckets) {
            String key = bucket.getKeyAsString();
            brandList.add(key);
        }
        return brandList;
    }

    private static void buildAggregation(SearchRequest request) {
        request.source().aggregation(AggregationBuilders
                .terms("brandAgg")
                .field("brand")
                .size(100));
        request.source().aggregation(AggregationBuilders
                .terms("cityAgg")
                .field("city")
                .size(100));
        request.source().aggregation(AggregationBuilders
                .terms("starNameAgg")
                .field("starName")
                .size(100));
    }

    private static void buildBasicQuery(RequestParams requestParams, SearchRequest request) {
        // 1. boolQuery
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        // must
        String key = requestParams.getKey();
        if (key == null || "".equals(key)) {
            boolQuery.must(QueryBuilders.matchAllQuery());
        } else {
            boolQuery.must(QueryBuilders.matchQuery("all", key));
        }
        // filter
        if (requestParams.getCity() != null && !"".equals(requestParams.getCity())) {
            boolQuery.filter(QueryBuilders.termQuery("city", requestParams.getCity()));
        }
        if (requestParams.getBrand() != null && !"".equals(requestParams.getBrand())) {
            boolQuery.filter(QueryBuilders.termQuery("brand", requestParams.getBrand()));
        }
        if (requestParams.getStarName() != null && !"".equals(requestParams.getStarName())) {
            boolQuery.filter(QueryBuilders.termQuery("starName", requestParams.getStarName()));
        }
        if (requestParams.getMinPrice() != null && requestParams.getMaxPrice() != null) {
            boolQuery.filter(QueryBuilders.rangeQuery("price")
                    .gte(requestParams.getMinPrice())
                    .lte(requestParams.getMaxPrice()));
        }
        //2. functionScoreQuery
        FunctionScoreQueryBuilder functionScoreQuery =
                QueryBuilders.functionScoreQuery(
                        boolQuery,
                        new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                                        QueryBuilders.termQuery("isAD", true),
                                        ScoreFunctionBuilders.weightFactorFunction(10)
                                )
                        });

        request.source().query(boolQuery);
    }

    private PageResult handleResponse(SearchResponse response) {
        SearchHits hits = response.getHits();

        long total = hits.getTotalHits().value;

        SearchHit[] docs = hits.getHits();

        List<HotelDoc> list = new ArrayList<>();

        for (SearchHit doc : docs) {
            String json = doc.getSourceAsString();

            HotelDoc hotelDoc = JSON.parseObject(json, HotelDoc.class);

            Object[] sortValues = doc.getSortValues();

            if (sortValues.length > 0) {
                hotelDoc.setDistance(sortValues[0]);
            }

            list.add(hotelDoc);
        }

        return new PageResult(total, list);
    }
}
