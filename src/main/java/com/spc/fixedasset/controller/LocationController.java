package com.spc.fixedasset.controller;

import com.spc.fixedasset.dto.AssetLocationResponse;
import com.spc.fixedasset.dto.LocationResponse;
import com.spc.fixedasset.service.LocationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Read-only location endpoints; never writes to F2_FIXED_ASSET or F2_FIXED_ASSET_MAP. */
@RestController
@RequestMapping("/api")
public class LocationController {

    private final LocationService service;

    public LocationController(LocationService service) {
        this.service = service;
    }

    @GetMapping("/locations")
    public List<LocationResponse> getLocations(@RequestParam(required = false) String fac,
                                               @RequestParam(required = false) String div,
                                               @RequestParam(required = false) String floor,
                                               @RequestParam(required = false) String assetFactory) {
        return service.getLocations(fac, div, floor, assetFactory);
    }

    @GetMapping("/assets/with-location")
    public List<AssetLocationResponse> getAssetsWithLocation(@RequestParam(required = false) String factory,
                                                             @RequestParam(required = false) String div) {
        return service.getAssetsWithLocation(factory, div);
    }

    @GetMapping("/assets/{code}/location")
    public AssetLocationResponse getAssetLocation(@PathVariable String code) {
        return service.getAssetLocation(code);
    }
}
