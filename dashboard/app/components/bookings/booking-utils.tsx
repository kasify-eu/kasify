import {
  IconBrandAirbnb,
  IconBrandBooking,
  IconWorld,
} from "@tabler/icons-react";
import type { ReactNode } from "react";
import type { Booking } from "~/types/bookings";

export function formatDateRange(booking: Booking) {
  try {
    const fromDate = new Date(booking.from);
    const toDate = new Date(booking.to);

    const diffTime = Math.abs(toDate.getTime() - fromDate.getTime());
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    const nights = diffDays === 1 ? "1 Night" : `${diffDays} Nights`;

    const formatDate = (date: Date) => {
      const month = date.toLocaleString("en-US", { month: "short" });
      const day = date.getDate();
      return `${month} ${day}`;
    };

    return {
      dateRange: `${formatDate(fromDate)} - ${formatDate(toDate)}`,
      nights,
    };
  } catch {
    return {
      dateRange: `${booking.from} - ${booking.to}`,
      nights: "-",
    };
  }
}

export function formatGuestCount(booking: Booking) {
  const adults = booking.adults_count || 0;
  const kids = (booking.children_count || 0) + (booking.infants_count || 0);
  if (adults === 0 && kids === 0) return "—";
  const parts = [];
  if (adults > 0) parts.push(`${adults} Adult${adults > 1 ? "s" : ""}`);
  if (kids > 0) parts.push(`${kids} Kid${kids !== 1 ? "s" : ""}`);
  return parts.join(", ");
}

export function getGuestName(booking: Booking) {
  return (
    `${booking.guest_first_name || ""} ${booking.guest_last_name || ""}`.trim() ||
    "Unknown Guest"
  );
}

/**
 * Bare channel icon (no surrounding box) + a human label, for composing into a shared
 * status group. Returns null when the channel is unknown. `ChannelBadge` keeps wrapping
 * this in its own bordered pill for the standalone usages.
 */
export function getChannelIcon(
  channel: string | null | undefined,
): { icon: ReactNode; label: string } | null {
  if (!channel) return null;

  const channelLower = channel.toLowerCase();

  if (channelLower.includes("airbnb")) {
    return { icon: <IconBrandAirbnb size={16} className="text-[#FF5A5F]" />, label: "Airbnb" };
  }
  if (channelLower.includes("booking")) {
    return { icon: <IconBrandBooking size={16} className="text-[#003580]" />, label: "Booking.com" };
  }
  return { icon: <IconWorld size={16} className="text-blue-600" />, label: "Direct" };
}

export function ChannelBadge({ channel }: { channel: string | null | undefined }) {
  if (!channel) {
    return null;
  }

  const channelLower = channel.toLowerCase();

  if (channelLower.includes("airbnb")) {
    return (
      <div className="inline-flex items-center gap-2 px-2 py-1 rounded-lg border border-slate-200 bg-white text-xs font-medium text-slate-700">
        <IconBrandAirbnb size={16} className="text-[#FF5A5F]" />
      </div>
    );
  }

  if (channelLower.includes("booking")) {
    return (
      <div className="inline-flex items-center gap-2 px-2 py-1 rounded-lg border border-slate-200 bg-white text-xs font-medium text-slate-700">
        <IconBrandBooking size={16} className="text-[#003580]" />
      </div>
    );
  }

  return (
    <div className="inline-flex items-center gap-2 px-2 py-1 rounded-lg border border-slate-200 bg-white text-xs font-medium text-slate-700">
      <IconWorld size={16} className="text-blue-600" />
    </div>
  );
}
