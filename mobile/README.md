# Zestivo Mobile

React Native + Expo + TypeScript customer storefront foundation. It reads restaurant and menu data from the tenant-aware backend and includes loading, error, empty, search, category-filter and theme states. Authentication, secure credential storage, checkout, order history and push notifications are not yet implemented.

## Run on Windows

Install Node.js 22.13+ and Android Studio / an Android emulator. Configure `EXPO_PUBLIC_API_BASE_URL` to a tenant hostname served by the API (not the apex landing domain), then run:

```powershell
cd mobile
npm install
npx expo start
```

Press `a` to open Android. On a physical device, use a reachable development hostname; `localhost` refers to the device itself. Tenant hostname routing must be configured and verified by the backend. Never send an untrusted tenant ID as a substitute for hostname verification.

## Type check

```powershell
npm run typecheck
```
