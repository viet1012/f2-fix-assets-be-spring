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

    /**
     * Assets with their matched MAP location.
     *
     * @param factory filters on the asset's {@code F2_FIXED_ASSET.Factory} (e.g. "Factory 2")
     * @param div     filters on the asset's {@code F2_FIXED_ASSET.Div}
     * @param fac     filters on {@code F2_FIXED_ASSET_MAP.Fac} of the matched row (e.g. "Fac_A"), not on the asset;
     *                unmatched assets (matchLevel NONE) are left out when it is given
     */
    @GetMapping("/assets/with-location")
    public List<AssetLocationResponse> getAssetsWithLocation(@RequestParam(required = false) String factory,
                                                             @RequestParam(required = false) String div,
                                                             @RequestParam(required = false) String fac) {
        return service.getAssetsWithLocation(factory, div, fac);
    }

    @GetMapping("/assets/{code}/location")
    public AssetLocationResponse getAssetLocation(@PathVariable String code) {
        return service.getAssetLocation(code);
    }
}
