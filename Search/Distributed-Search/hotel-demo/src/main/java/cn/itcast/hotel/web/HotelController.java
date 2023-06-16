package cn.itcast.hotel.web;

import cn.itcast.hotel.pojo.PageResult;
import cn.itcast.hotel.pojo.RequestParams;
import cn.itcast.hotel.service.IHotelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/hotel")
public class HotelController {

    @Autowired
    private IHotelService iHotelService;

    @PostMapping("/list")
    public PageResult search(@RequestBody RequestParams requestParams) {
        return iHotelService.search(requestParams);
    }

    @PostMapping("/filters")
    public Map<String, List<String>> getFilters(@RequestBody RequestParams requestParams) {
        return iHotelService.filter(requestParams);
    }

    @GetMapping("/suggestion")
    public List<String> getSuggestion(@RequestParam("key") String key) {
        return iHotelService.getSuggestion(key);
    }
}
