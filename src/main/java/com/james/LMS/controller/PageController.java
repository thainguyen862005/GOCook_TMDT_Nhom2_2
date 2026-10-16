package com.james.LMS.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Production entry points for the React single-page app.
 * During development Vite serves these routes on http://localhost:5173.
 */
@Controller
public class PageController {

  private static final String REACT_APP = "forward:/app/index.html";

  @GetMapping({
      "/", "/login", "/register", "/logout", "/forgot-password", "/reset-password",
      "/verify-email", "/chefs", "/chef-detail", "/chef-an-nhien", "/menus",
      "/menu-detail", "/menu-vegetarian", "/offers", "/help", "/request", "/pages",
      "/quotes", "/review", "/matching", "/become-chef", "/chat"
  })
  public String publicPages() {
    return REACT_APP;
  }

  @GetMapping({
      "/account", "/cart", "/cart-empty", "/cart-vegetarian", "/checkout",
      "/checkout-vegetarian", "/order-detail", "/orders", "/success",
      "/success-vegetarian", "/tracking", "/payment-result"
  })
  @PreAuthorize("isAuthenticated()")
  public String customerPages() {
    return REACT_APP;
  }

  @GetMapping({
      "/seller", "/seller/order-detail", "/seller/orders", "/seller/product-edit",
      "/seller/product-new", "/seller/products", "/seller/revenue", "/seller/schedule"
  })
  @PreAuthorize("hasAuthority('ROLE_INSTRUCTOR')")
  public String sellerPages() {
    return REACT_APP;
  }

  @GetMapping({
      "/admin", "/admin/users", "/admin/user-edit", "/admin/products",
      "/admin/product-new", "/admin/product-edit", "/admin/orders",
      "/admin/order-detail", "/admin/statistics"
  })
  @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
  public String adminPages() {
    return REACT_APP;
  }
}
