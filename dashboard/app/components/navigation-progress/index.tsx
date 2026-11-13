import { useNavigation } from "react-router";
import classes from "./index.module.css";

/**
 * Thin indeterminate progress bar at the top of the viewport while a route
 * navigation (loader) is pending, so the app doesn't feel frozen on slow
 * SSR loaders. The animation ramps quickly then crawls; it unmounts as soon
 * as the navigation settles.
 */
export function NavigationProgress() {
  const navigation = useNavigation();
  if (navigation.state === "idle") return null;
  return <div className={classes.bar} role="progressbar" aria-label="Loading" />;
}
