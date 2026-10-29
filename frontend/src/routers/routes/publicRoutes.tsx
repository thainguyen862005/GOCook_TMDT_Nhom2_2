import type { RouteObject } from 'react-router-dom';
import { Navigate } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';
import { SiteLayout } from '../../layouts/SiteLayout';
import { ChefDetailPage } from '../../pages/chef/ChefDetailPage';
import { ChefListPage } from '../../pages/chef/ChefListPage';
import { HomePage } from '../../pages/home/HomePage';
import { MenuDetailPage } from '../../pages/menu/MenuDetailPage';
import { MenuListPage } from '../../pages/menu/MenuListPage';
import { OffersPage } from '../../pages/offer/OffersPage';
import { MatchingPage } from '../../pages/request/MatchingPage';
import { ReviewPage } from '../../pages/review/ReviewPage';
import { BecomeChefPage } from '../../pages/seller/BecomeChefPage';
import { HelpPage } from '../../pages/support/HelpPage';
import { PageCatalogPage } from '../../pages/system/PageCatalogPage';

export const publicRoutes: RouteObject[] = [
  {
    element: <SiteLayout />,
    children: [
      { path: ROUTES.HOME, element: <HomePage /> },
      { path: ROUTES.CHEFS, element: <ChefListPage /> },
      { path: ROUTES.CHEF_DETAIL, element: <ChefDetailPage /> },
      { path: ROUTES.CHEF_AN_NHIEN, element: <Navigate to={ROUTES.CHEFS} replace /> },
      { path: ROUTES.MENUS, element: <MenuListPage /> },
      { path: ROUTES.MENU_DETAIL, element: <MenuDetailPage /> },
      { path: ROUTES.MENU_VEGETARIAN, element: <Navigate to={`${ROUTES.MENUS}?q=chay`} replace /> },
      { path: ROUTES.OFFERS, element: <OffersPage /> },
      { path: ROUTES.HELP, element: <HelpPage /> },
      { path: ROUTES.PAGE_CATALOG, element: <PageCatalogPage /> },
      { path: ROUTES.REVIEW, element: <ReviewPage /> },
      { path: ROUTES.MATCHING, element: <MatchingPage /> },
      { path: ROUTES.BECOME_CHEF, element: <BecomeChefPage /> },
    ],
  },
];
