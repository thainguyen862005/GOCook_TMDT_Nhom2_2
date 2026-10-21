package com.james.LMS.admin.controller;

import com.james.LMS.admin.dto.response.AdminDashboardResponse;
import com.james.LMS.admin.service.AdminDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public AdminDashboardResponse getDashboard() {
        return new AdminDashboardResponse(
                dashboardService.countUsers(),
                dashboardService.countPublishedProducts(),
                dashboardService.countOrders(),
                dashboardService.countActiveChefs(),
                dashboardService.todayRevenue(),
                dashboardService.thisMonthRevenue(),
                dashboardService.thisYearRevenue(),
                dashboardService.completedRevenue(),
                dashboardService.recentOrders()
        );
    }
}