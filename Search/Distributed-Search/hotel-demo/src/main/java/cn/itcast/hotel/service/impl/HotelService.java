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
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class HotelService extends ServiceImpl<HotelMapper, Hotel> implements IHotelService {

    @Autowired
    private RestHighLevelClient client;

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
