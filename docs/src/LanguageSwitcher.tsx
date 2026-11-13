import { useEffect } from "react";
import type { Location, NavigateFunction } from "react-router";
import "./LanguageSwitcher.css";
import {
  DEFAULT_LOCALE,
  getBasePathAndLocale,
  LOCALES,
  pathForLocale,
  savePreferredLocale,
} from "./locales.js";

type Props = {
  location: Location;
  navigate: NavigateFunction;
};

export const LanguageSwitcher = ({ location, navigate }: Props) => {
  const { basePath, locale: currentLocale } = getBasePathAndLocale(
    location.pathname,
  );

  // Expose the active locale on the root element so CSS can hide the
  // opposite-locale top-nav tab. Runs on navigation.
  useEffect(() => {
    if (typeof document === "undefined") return;
    document.documentElement.dataset.locale = currentLocale;
  }, [currentLocale]);

  return (
    <div className="flex items-center gap-0.5 text-xs font-medium">
      {LOCALES.map(({ code, label }) => {
        const target = pathForLocale(basePath, code);
        const isCurrent = code === currentLocale;
        const unavailable = target === null;

        if (unavailable) {
          return (
            <span
              key={code}
              className="px-2 py-1 opacity-30 cursor-not-allowed"
              title={`No ${label} translation available — falling back to ${DEFAULT_LOCALE.toUpperCase()}`}
            >
              {code.toUpperCase()}
            </span>
          );
        }

        return (
          <button
            key={code}
            type="button"
            onClick={() => {
              if (!isCurrent && target) {
                savePreferredLocale(code);
                navigate(target);
              }
            }}
            className={
              isCurrent
                ? "px-2 py-1 rounded bg-muted font-semibold"
                : "px-2 py-1 rounded opacity-70 hover:opacity-100 hover:bg-muted/60 transition-opacity"
            }
            aria-current={isCurrent ? "page" : undefined}
            aria-label={`Switch language to ${label}`}
          >
            {code.toUpperCase()}
          </button>
        );
      })}
    </div>
  );
};
