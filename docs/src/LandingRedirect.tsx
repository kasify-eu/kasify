import { useEffect } from "react";
import { useNavigate } from "react-router";
import { detectPreferredLocale } from "./locales.js";

// Neutral entry page; each locale resolves it as `/<locale>/introduction`.
const LANDING_BASE_PATH = "/introduction";

export const LandingRedirect = () => {
  const navigate = useNavigate();

  useEffect(() => {
    const locale = detectPreferredLocale();
    navigate(`/${locale}${LANDING_BASE_PATH}`, { replace: true });
  }, [navigate]);

  return null;
};
