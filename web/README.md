# MealMinutes Web

React + TypeScript + Vite application. The initial increment provides a responsive Zestivo landing page and tenant storefront menu reader.

## Run locally

Requires Node.js 22+.

```powershell
cd web
npm install
npm run dev
```

Vite serves the landing page at http://localhost:5173. Set `VITE_API_BASE_URL` when the API is not served from the same origin. Storefront API calls are:

- `GET /api/v1/storefront/menu/restaurant`
- `GET /api/v1/storefront/menu/categories`
- `GET /api/v1/storefront/menu/items?page=0&size=48`

A verified tenant hostname and configured tenant datasource are required for real menu data. Restaurant discovery, cart/checkout, dashboard and platform admin UI are not yet implemented. The landing page does not fabricate restaurant metrics or menu records.

## Checks

```powershell
npm test
npm run build
```
