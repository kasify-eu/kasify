import {
  IconBattery,
  IconBattery1,
  IconBattery2,
  IconBattery3,
  IconBattery4,
  IconBatteryCharging,
  IconLock,
  IconLockOpen,
} from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import type {SmartLock} from "~/types/properties";

interface SmartLockCardProps {
  smartLock: SmartLock;
  onUnlock?: (smartLockId: string) => void;
  onLock?: (smartLockId: string) => void;
  isLoading?: boolean;
}

/**
 * Convert Nuki lock state number to display state
 * Nuki lock states:
 * 0 = uncalibrated, 1 = locked, 2 = unlocking, 3 = unlocked,
 * 4 = locking, 5 = unlatched, 6 = unlocked (lock 'n' go), 7 = unlatching
 */
type LockDisplayState = "locked" | "unlocked" | "unlocking" | "locking" | "unknown";

const getLockDisplayState = (lockState?: number): LockDisplayState => {
  if (lockState === undefined) return "unknown";
  switch (lockState) {
    case 1:
      return "locked";
    case 2:
      return "unlocking";
    case 3:
    case 5:
    case 6:
      return "unlocked";
    case 4:
    case 7:
      return "locking";
    default:
      return "unknown";
  }
};

const getStateKey = (state: LockDisplayState): string => {
  switch (state) {
    case "locked":   return "nukiSmartlockCard.state.locked";
    case "unlocked": return "nukiSmartlockCard.state.unlocked";
    case "unlocking": return "nukiSmartlockCard.state.unlocking";
    case "locking":  return "nukiSmartlockCard.state.locking";
    default:         return "nukiSmartlockCard.state.unknown";
  }
};

const getStatusStyles = (state: LockDisplayState) => {
  switch (state) {
    case "locked":
      return {
        bg: "bg-emerald-100",
        text: "text-emerald-700",
        dot: "bg-emerald-500",
      };
    case "unlocked":
      return {
        bg: "bg-amber-100",
        text: "text-amber-700",
        dot: "bg-amber-500",
      };
    case "unlocking":
    case "locking":
      return {
        bg: "bg-blue-100",
        text: "text-blue-700",
        dot: "bg-blue-500",
      };
    default:
      return {
        bg: "bg-slate-100",
        text: "text-slate-700",
        dot: "bg-slate-500",
      };
  }
};

const getBatteryIcon = (percentage?: number, charging?: boolean) => {
  if (charging) return IconBatteryCharging;
  if (!percentage) return IconBattery;
  if (percentage > 87) return IconBattery4;
  if (percentage > 62) return IconBattery3;
  if (percentage > 37) return IconBattery2;
  if (percentage > 12) return IconBattery1;
  return IconBattery;
};

export function SmartLockCard({
  smartLock,
  onUnlock,
  onLock,
  isLoading = false,
}: SmartLockCardProps) {
  const { t } = useTranslation();
  const isOffline = smartLock.offline === true;
  const displayState = isOffline ? "unknown" : getLockDisplayState(smartLock.lock_state);
  const isLocked = displayState === "locked";
  const isProcessing = displayState === "unlocking" || displayState === "locking";
  const statusStyles = isOffline
    ? { bg: "bg-slate-100", text: "text-slate-500", dot: "bg-slate-400" }
    : getStatusStyles(displayState);
  const BatteryIcon = getBatteryIcon(smartLock.battery_level, smartLock.battery_charging);

  return (
      <div
          className="group relative bg-white  rounded-3xl p-8 border border-slate-100  hover:shadow-2xl hover:shadow-slate-200/50 transition-all duration-300">
        {/* Accent bar at top */}
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-24 h-1 bg-yellow-400 rounded-b-full"/>

        {/* Header with icon and status */}
        <div className="flex justify-between items-start mb-8">
          {/* Lock Icon */}
          <div className="w-14 h-14 flex items-center justify-center bg-slate-50  rounded-2xl">
            {isLocked ? (
                <IconLock size={28} stroke={1.5} className="text-slate-700 "/>
            ) : (
                <IconLockOpen size={28} stroke={1.5} className="text-slate-700 "/>
            )}
          </div>

          {/* Status and battery */}
          <div className="flex flex-col items-end gap-2">
            {/* Status badge */}
            <span
                className={`inline-flex items-center px-3 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider ${statusStyles.bg} ${statusStyles.text}`}
            >
            <span className={`w-1.5 h-1.5 rounded-full ${statusStyles.dot} mr-2`}/>
              {isOffline ? t("nukiSmartlockCard.state.offline") : t(getStateKey(displayState))}
          </span>

            {/* Battery indicator */}
            {smartLock.battery_level !== undefined && (
                <div className="flex items-center gap-1 text-slate-400">
                  <BatteryIcon size={16} stroke={1.5}/>
                  <span className="text-[10px] font-bold">{smartLock.battery_level}%</span>
                </div>
            )}
          </div>
        </div>

        {/* Lock name */}
        <h3 className="text-md font-bold mb-1 text-slate-900 ">
          {smartLock.name}
        </h3>

        {/* Vendor/model */}
        <p className="text-xs text-slate-400 font-mono tracking-wider uppercase">
          {smartLock.vendor}
        </p>

        {/* Toggle buttons
      <div className="flex items-center justify-between p-1 bg-slate-100  rounded-2xl">
        <button
          type="button"
          onClick={() => onLock?.(smartLock.id)}
          disabled={isLocked || isProcessing || isLoading}
          className={`flex-1 py-3 px-4 rounded-xl text-sm font-bold transition-all ${
            isLocked
              ? "bg-white  shadow-sm text-slate-900 "
              : "text-slate-400 hover:text-slate-600 disabled:opacity-50 disabled:cursor-not-allowed"
          }`}
        >
          Lock
        </button>
        <button
          type="button"
          onClick={() => onUnlock?.(smartLock.id)}
          disabled={!isLocked || isProcessing || isLoading}
          className={`flex-1 py-3 px-4 rounded-xl text-sm font-bold transition-all ${
            !isLocked
              ? "bg-white shadow-sm text-slate-900"
              : "text-slate-400 hover:text-slate-600 disabled:opacity-50 disabled:cursor-not-allowed"
          }`}
        >
          Unlock
        </button>
      </div>
      */}
      </div>
  );
}
