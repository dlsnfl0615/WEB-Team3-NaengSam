import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@fontsource-variable/inter";
import "./app/styles/theme.css";
import "./app/styles/index.css";
import "./app/styles/App.css";
import App from "./app/App";
import { RoleProvider } from "./shared/lib/role/RoleProvider";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <RoleProvider>
      <App />
    </RoleProvider>
  </StrictMode>,
);
