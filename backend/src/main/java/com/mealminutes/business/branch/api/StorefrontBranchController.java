package com.mealminutes.business.branch.api;

import com.mealminutes.business.branch.service.RestaurantBranchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/storefront/branches")
@ConditionalOnProperty(prefix = "mealminutes.tenant-provisioning", name = "enabled", havingValue = "true")
public class StorefrontBranchController {
    private final RestaurantBranchService branches;

    public StorefrontBranchController(RestaurantBranchService branches) {
        this.branches = branches;
    }

    @GetMapping
    public List<RestaurantBranchService.BranchResponse> list() {
        return branches.listBranches(false);
    }

    @GetMapping("/{branchId}/operating-hours")
    public List<RestaurantBranchService.OperatingHoursResponse> operatingHours(@PathVariable String branchId) {
        return branches.listOperatingHours(branchId, true);
    }
}
