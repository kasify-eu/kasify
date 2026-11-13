import {
  IconBrush,
  IconCalendar,
  IconTool,
} from "@tabler/icons-react";
import type { Activity } from "~/types/activities";
import {
  ChannelBadge,
  formatGuestCount,
} from "~/components/bookings/booking-utils";
import { typeBadgeClasses } from "~/components/calendar-shared/styles";

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

function formatDate(dateStr: string) {
  const date = new Date(dateStr);
  return date.toLocaleString("en-US", { month: "short", day: "numeric" });
}

function ActivityDateCell({ activity }: { activity: Activity }) {
  if (activity.type === "booking" && activity.booking) {
    const from = formatDate(activity.booking.from);
    const to = formatDate(activity.booking.to);
    const diffDays = Math.ceil(
      (new Date(activity.booking.to).getTime() - new Date(activity.booking.from).getTime()) /
        (1000 * 60 * 60 * 24),
    );
    const nights = diffDays === 1 ? "1 night" : `${diffDays} nights`;
    return (
      <div className="flex flex-col">
        <span className="font-medium text-xs text-slate-900">
          {from} — {to}
        </span>
        <span className="text-xs text-slate-500">{nights}</span>
      </div>
    );
  }

  if (activity.date) {
    const dateLabel = formatDate(activity.date);
    const timeLabel =
      activity.time && activity.end_time
        ? `${activity.time} – ${activity.end_time}`
        : activity.time ?? null;
    return (
      <div className="flex flex-col">
        <span className="font-medium text-xs text-slate-900">{dateLabel}</span>
        {timeLabel && <span className="text-xs text-slate-500">{timeLabel}</span>}
      </div>
    );
  }

  return <span className="text-xs text-slate-400">—</span>;
}

interface ActivitiesTableProps {
  activities: Activity[];
  onBookingClick?: (bookingId: string) => void;
  onTaskClick?: (taskId: string) => void;
  showHeader?: boolean;
}

export function ActivitiesTable({
  activities,
  onBookingClick,
  onTaskClick,
  showHeader = true,
}: ActivitiesTableProps) {
  return (
    <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse">
          {showHeader && (
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                <th className="w-4 pl-3 pr-0 py-4"></th>
                <th className="px-3 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider">
                  Type
                </th>
                <th className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider">
                  Date
                </th>
                <th className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider">
                  Name
                </th>
                <th className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider">
                  Guests
                </th>
                <th className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider w-px whitespace-nowrap">
                  Channel
                </th>
              </tr>
            </thead>
          )}
          <tbody className="divide-y divide-slate-100">
            {activities.map((activity) => {
              const isBooking = activity.type === "booking";
              const isTask = activity.task != null;
              const today = new Date().toISOString().split("T")[0];
              const isActive = isBooking && activity.booking
                ? activity.booking.from <= today && today <= activity.booking.to
                : activity.date === today;

              const handleClick = () => {
                if (isBooking && activity.booking && onBookingClick) {
                  onBookingClick(activity.booking.id);
                } else if (isTask && activity.task && onTaskClick) {
                  onTaskClick(activity.task.id);
                }
              };

              const isClickable =
                (isBooking && !!onBookingClick) || (isTask && !!onTaskClick);

              const name = isBooking && activity.booking
                ? `${activity.booking.guest_first_name ?? ""} ${(activity.booking as any).guest_last_name ?? ""}`.trim() || "Unknown Guest"
                : activity.task?.assigned_to_name ?? "—";

              return (
                <tr
                  key={activity.id}
                  onClick={isClickable ? handleClick : undefined}
                  className={`group transition-colors ${isClickable ? "hover:bg-slate-50 cursor-pointer" : ""}`}
                >
                  <td className="w-4 pl-3 pr-0 py-3.5">
                    {isActive && (
                      <span className="inline-block w-2 h-2 rounded-full bg-green-500" />
                    )}
                  </td>
                  <td className="px-3 py-3.5 w-35">
                    <ActivityTypeBadge type={activity.type} />
                  </td>
                  <td className="px-2.5 py-3.5 w-35">
                    <ActivityDateCell activity={activity} />
                  </td>
                  <td className="px-2.5 py-3.5">
                    <p className="font-medium text-xs text-slate-900">{name}</p>
                  </td>
                  <td className="px-2.5 py-3.5">
                    {isBooking && activity.booking ? (
                      <span className="text-xs text-slate-700">
                        {formatGuestCount(activity.booking as any)}
                      </span>
                    ) : (
                      <span className="text-xs text-slate-400">—</span>
                    )}
                  </td>
                  <td className="px-2 py-3.5 w-30 text-right">
                    {isBooking && activity.booking ? (
                      <ChannelBadge channel={activity.booking.channel} />
                    ) : (
                      <></>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
