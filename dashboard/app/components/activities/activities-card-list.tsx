import type { Activity } from "~/types/activities";
import {
  ChannelBadge,
  formatGuestCount,
} from "~/components/bookings/booking-utils";
import {
  IconBrush,
  IconCalendar,
  IconTool,
  IconUsers,
} from "@tabler/icons-react";
import { typeBadgeClasses } from "~/components/calendar-shared/styles";

function formatDate(dateStr: string) {
  const date = new Date(dateStr);
  return date.toLocaleString("en-US", { month: "short", day: "numeric" });
}

function ActivityTypeBadge({ type }: { type: Activity["type"] }) {
  if (type === "booking") {
    return (
      <div className={`inline-flex items-center gap-1.5 px-2 py-1 rounded-lg border text-xs font-medium ${typeBadgeClasses("booking")}`}>
        <IconCalendar size={13} />
        Booking
      </div>
    );
  }
  if (type === "cleaning") {
    return (
      <div className={`inline-flex items-center gap-1.5 px-2 py-1 rounded-lg border text-xs font-medium ${typeBadgeClasses("cleaning")}`}>
        <IconBrush size={13} />
        Cleaning
      </div>
    );
  }
  if (type === "maintenance") {
    return (
      <div className={`inline-flex items-center gap-1.5 px-2 py-1 rounded-lg border text-xs font-medium ${typeBadgeClasses("maintenance")}`}>
        <IconTool size={13} />
        Maintenance
      </div>
    );
  }
  return null;
}

interface ActivitiesCardListProps {
  activities: Activity[];
  onBookingClick?: (bookingId: string) => void;
  onTaskClick?: (taskId: string) => void;
}

export function ActivitiesCardList({
  activities,
  onBookingClick,
  onTaskClick,
}: ActivitiesCardListProps) {
  return (
    <div className="space-y-3">
      {activities.map((activity) => {
        const isBooking = activity.type === "booking";
        const isTask = activity.task != null;
        const today = new Date().toISOString().split("T")[0];
        const isActive = isBooking && activity.booking
          ? activity.booking.from <= today && today <= activity.booking.to
          : activity.date === today;

        const isClickable =
          (isBooking && !!onBookingClick) || (isTask && !!onTaskClick);

        const handleClick = () => {
          if (isBooking && activity.booking && onBookingClick) {
            onBookingClick(activity.booking.id);
          } else if (isTask && activity.task && onTaskClick) {
            onTaskClick(activity.task.id);
          }
        };

        const name = isBooking && activity.booking
          ? `${activity.booking.guest_first_name ?? ""} ${(activity.booking as any).guest_last_name ?? ""}`.trim() || "Unknown Guest"
          : activity.task?.assigned_to_name ?? "—";

        const dateLabel = (() => {
          if (isBooking && activity.booking) {
            const from = formatDate(activity.booking.from);
            const to = formatDate(activity.booking.to);
            const diffDays = Math.ceil(
              (new Date(activity.booking.to).getTime() - new Date(activity.booking.from).getTime()) /
                (1000 * 60 * 60 * 24),
            );
            const nights = diffDays === 1 ? "1 night" : `${diffDays} nights`;
            return `${from} — ${to} · ${nights}`;
          }
          if (activity.date) {
            const d = formatDate(activity.date);
            const timeLabel =
              activity.time && activity.end_time
                ? `${activity.time} – ${activity.end_time}`
                : activity.time ?? null;
            return timeLabel ? `${d} · ${timeLabel}` : d;
          }
          return null;
        })();

        return (
          <div
            key={activity.id}
            onClick={isClickable ? handleClick : undefined}
            className={`bg-white rounded-xl p-4 shadow-sm border border-slate-100 ${isClickable ? "cursor-pointer active:bg-slate-50" : ""}`}
          >
            <div className="flex items-start justify-between gap-2">
              <div className="flex items-center gap-2">
                {isActive && (
                  <span className="inline-block w-2 h-2 rounded-full bg-green-500 shrink-0 mt-0.5" />
                )}
                <p className="text-base font-bold text-slate-900">{name}</p>
              </div>
              <ActivityTypeBadge type={activity.type} />
            </div>
            <div className="mt-3 space-y-1.5">
              {dateLabel && (
                <div className="flex items-center gap-2 text-sm text-slate-600">
                  <IconCalendar size={16} className="text-slate-400 shrink-0" />
                  <span>{dateLabel}</span>
                </div>
              )}
              {isBooking && activity.booking && (
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 text-sm text-slate-600">
                    <IconUsers size={16} className="text-slate-400 shrink-0" />
                    <span>{formatGuestCount(activity.booking as any)}</span>
                  </div>
                  <ChannelBadge channel={activity.booking.channel} />
                </div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
