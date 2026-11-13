import { useMemo } from "react";
import { Menu } from "@mantine/core";
import { useMediaQuery } from "@mantine/hooks";
import { useTranslation } from "react-i18next";
import classes from "./calendar-timeline.module.css";
import { CHANNEL_STYLE, TASK_STYLE } from "~/components/calendar-shared/styles";
import type {
  CalendarFilterKey,
  CalendarMultiDayTask,
  CalendarPickedEvent,
  CalendarRow,
  CalendarSingleDayTask,
  CalendarTask,
  CalendarTaskKind,
} from "./types";

interface CalendarTimelineProps {
  rows: CalendarRow[];
  /** Range start (inclusive). */
  rangeStart: Date;
  /** Number of days to render starting at rangeStart. */
  rangeDays: number;
  /** Reference "today" — used for the today indicator. Defaults to new Date(). */
  today?: Date;
  /** Filter which event types are visible. Defaults to all. */
  activeTypes?: Set<CalendarFilterKey>;
  onPick?: (event: CalendarPickedEvent) => void;
  /** Clicking an empty day cell requests creating a task for that property/day. */
  onCreate?: (propertyId: string, date: Date) => void;
}

const WD_NARROW = ["M", "T", "W", "T", "F", "S", "S"];
const DEFAULT_ACTIVE: Set<CalendarFilterKey> = new Set([
  "booking",
  "cleaning",
  "maintenance",
  "pool",
  "gardening",
  "unknown",
]);

function parseDate(s: string): Date {
  const dateOnly = s.length > 10 ? s.slice(0, 10) : s;
  const [y, m, d] = dateOnly.split("-").map(Number);
  return new Date(y, m - 1, d);
}
function dayKey(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
function addDays(d: Date, n: number): Date {
  const x = new Date(d);
  x.setDate(x.getDate() + n);
  return x;
}
function startOfDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}
function isWeekend(d: Date): boolean {
  const w = d.getDay();
  return w === 0 || w === 6;
}

interface DayCell {
  date: Date;
  key: string;
  isWeekend: boolean;
}

function buildDays(start: Date, count: number): DayCell[] {
  const out: DayCell[] = [];
  const s = startOfDay(start);
  for (let i = 0; i < count; i++) {
    const d = addDays(s, i);
    out.push({ date: d, key: dayKey(d), isWeekend: isWeekend(d) });
  }
  return out;
}

function dayIndex(dStr: string, days: DayCell[]): number {
  if (days.length === 0) return -1;
  const target = parseDate(dStr).getTime();
  const first = days[0].date.getTime();
  const idx = Math.round((target - first) / 86400000);
  return idx;
}

// Fraction of the day (0..1) implied by the time-of-day portion of an ISO
// datetime string. Reads digits directly to avoid TZ ambiguity (the backend
// labels property-local datetimes with a `Z` suffix). Pure-date strings
// (length < 16) collapse to 0.
function timeFraction(iso: string): number {
  if (iso.length < 16) return 0;
  const hh = parseInt(iso.slice(11, 13), 10);
  const mm = parseInt(iso.slice(14, 16), 10);
  if (Number.isNaN(hh) || Number.isNaN(mm)) return 0;
  return (hh * 60 + mm) / (24 * 60);
}

// Two-digit "HH:mm" pulled from the ISO string. Empty when no time is present.
function timeLabel(iso: string): string {
  return iso.length < 16 ? "" : iso.slice(11, 16);
}

function isSpanTask(t: CalendarTask): t is CalendarMultiDayTask {
  return t.span === true;
}

interface PlacedSpan {
  task: CalendarMultiDayTask;
  /** Continuous column position (fractional) where the bar begins. */
  start: number;
  /** Continuous column position (fractional) where the bar ends. */
  end: number;
  lane: number;
}

function layoutSpans(
  spans: CalendarMultiDayTask[],
  days: DayCell[],
): { placed: PlacedSpan[]; laneCount: number } {
  const items: { task: CalendarMultiDayTask; start: number; end: number }[] = [];
  for (const t of spans) {
    const startIdx = dayIndex(t.start.slice(0, 10), days);
    const endIdx = dayIndex(t.end.slice(0, 10), days);
    const start = Math.max(0, startIdx + timeFraction(t.start));
    const rawEnd = endIdx < 0 ? 0 : endIdx + timeFraction(t.end);
    const end = Math.min(days.length, rawEnd);
    if (end > start) items.push({ task: t, start, end });
  }
  items.sort((a, b) => a.start - b.start);

  const laneEnds: number[] = [];
  const placed: PlacedSpan[] = items.map((s) => {
    let lane = laneEnds.findIndex((e) => e <= s.start);
    if (lane === -1) {
      lane = laneEnds.length;
      laneEnds.push(s.end);
    } else {
      laneEnds[lane] = s.end;
    }
    return { ...s, lane };
  });
  return { placed, laneCount: laneEnds.length };
}

export function CalendarTimeline({
  rows,
  rangeStart,
  rangeDays,
  today = new Date(),
  activeTypes = DEFAULT_ACTIVE,
  onPick,
  onCreate,
}: CalendarTimelineProps) {
  const { t } = useTranslation();
  const days = useMemo(() => buildDays(rangeStart, rangeDays), [rangeStart, rangeDays]);
  const todayKey = useMemo(() => dayKey(startOfDay(today)), [today]);

  const isMobile = useMediaQuery("(max-width: 48em)");

  const colW = 44;
  const propColW = isMobile ? 110 : 220;

  // Lane geometry — booking bar on top, task lane below.
  const padTop = 8;
  const bookingHeight = 26;
  const gapBookingTask = 4;
  const taskBarH = 16;
  const taskBarGap = 3;
  const padBottom = 8;
  const chipH = 14;
  const bookingTop = padTop;

  const gridCols = `${propColW}px repeat(${days.length}, ${colW}px)`;
  const totalWidth = propColW + days.length * colW;

  const showBookings = activeTypes.has("booking");

  return (
    <div className={classes.wrap}>
      {/* Header row */}
      <div className={classes.head} style={{ gridTemplateColumns: gridCols, width: totalWidth }}>
        <div className={classes.corner}>
          <div className={classes.cornerTitle}>Property</div>
        </div>
        {days.map((d) => {
          const isToday = d.key === todayKey;
          return (
            <div
              key={d.key}
              className={classes.day}
              data-weekend={d.isWeekend ? "true" : "false"}
              data-today={isToday ? "true" : "false"}
            >
              <div className={classes.dnum}>{d.date.getDate()}</div>
              <div className={classes.dwd}>{WD_NARROW[(d.date.getDay() + 6) % 7]}</div>
            </div>
          );
        })}
      </div>

      {/* Body */}
      <div className={classes.body} style={{ width: totalWidth }}>
        {rows.map((p) => {
          const propBookings = p.bookings;
          const propTasks = p.tasks.filter((t) => activeTypes.has(t.kind));

          // Single-day chips bucketed by day index
          const taskBuckets: Record<number, CalendarSingleDayTask[]> = {};
          for (const t of propTasks) {
            if (isSpanTask(t)) continue;
            const idx = dayIndex(t.date, days);
            if (idx < 0 || idx >= days.length) continue;
            (taskBuckets[idx] = taskBuckets[idx] ?? []).push(t);
          }
          for (const arr of Object.values(taskBuckets)) {
            arr.sort((a, b) => a.id.localeCompare(b.id));
          }

          // Multi-day task spans laid out in stacking sub-lanes
          const spanTasks = propTasks.filter(isSpanTask);
          const { placed: placedSpans, laneCount: spanLaneCount } = layoutSpans(
            spanTasks,
            days,
          );

          const hasSingleDayChips = Object.keys(taskBuckets).length > 0;
          const chipLanes = hasSingleDayChips ? 1 : 0;
          const taskRows = spanLaneCount + chipLanes;
          const taskLaneTop = bookingTop + bookingHeight + gapBookingTask;
          const taskLaneH =
            taskRows > 0 ? taskRows * taskBarH + (taskRows - 1) * taskBarGap : 0;
          const rowH =
            padTop +
            bookingHeight +
            (taskRows > 0 ? gapBookingTask + taskLaneH : 0) +
            padBottom;
          const chipLaneTop = taskLaneTop + spanLaneCount * (taskBarH + taskBarGap);
          const chipTop = chipLaneTop + Math.max(0, (taskBarH - chipH) / 2);

          return (
            <div
              key={p.id}
              className={classes.row}
              style={{ height: rowH, gridTemplateColumns: gridCols, width: totalWidth }}
            >
              <div className={classes.prop}>
                <div className={classes.propName}>{p.name}</div>
              </div>
              {days.map((d) => {
                const handleCreate = onCreate
                  ? () => onCreate(p.id, d.date)
                  : undefined;
                return (
                  <div
                    key={d.key}
                    className={
                      handleCreate
                        ? `${classes.cell} ${classes.cellClickable}`
                        : classes.cell
                    }
                    data-weekend={d.isWeekend ? "true" : "false"}
                    data-today={d.key === todayKey ? "true" : "false"}
                    role={handleCreate ? "button" : undefined}
                    tabIndex={handleCreate ? 0 : undefined}
                    aria-label={
                      handleCreate ? t("calendar.addTask") : undefined
                    }
                    onClick={handleCreate}
                    onKeyDown={
                      handleCreate
                        ? (e) => {
                            if (e.key === "Enter" || e.key === " ") {
                              e.preventDefault();
                              handleCreate();
                            }
                          }
                        : undefined
                    }
                  >
                    {handleCreate && (
                      <span className={classes.cellAdd} aria-hidden="true">
                        +
                      </span>
                    )}
                  </div>
                );
              })}

              {/* Booking bars (top lane) */}
              {showBookings &&
                propBookings.map((b) => {
                  const startIdx = dayIndex(b.in, days);
                  const endIdx = dayIndex(b.out, days);
                  const start = Math.max(0, startIdx);
                  const end = endIdx < 0 ? 0 : Math.min(days.length, endIdx);
                  if (end <= start) return null;
                  const ch = CHANNEL_STYLE[b.channel];
                  const left = propColW + start * colW + colW * 0.5;
                  const width = (end - start) * colW - 2;
                  return (
                    <button
                      key={b.id}
                      type="button"
                      className={classes.bar}
                      style={{
                        left,
                        width,
                        background: ch.bg,
                        borderColor: ch.bar,
                        color: ch.ink,
                        top: bookingTop,
                        height: bookingHeight,
                      }}
                      onClick={() =>
                        onPick?.({ type: "booking", booking: b })
                      }
                      title={`${b.guest} · ${ch.name}`}
                    >
                      <span className={classes.barLabel}>{b.guest}</span>
                      {b.channel !== "unknown" && (
                        <span
                          className={classes.barChannel}
                          style={{ background: ch.bar }}
                        >
                          {ch.name[0]}
                        </span>
                      )}
                    </button>
                  );
                })}

              {/* Multi-day task bars (task lane, stacking sub-lanes) */}
              {placedSpans.map(({ task, start, end, lane }) => {
                const tk = TASK_STYLE[task.kind];
                const left = propColW + start * colW + 2;
                const width = (end - start) * colW - 4;
                const top = taskLaneTop + lane * (taskBarH + taskBarGap);
                const startTime = timeLabel(task.start);
                const endTime = timeLabel(task.end);
                const range =
                  startTime && endTime ? `${startTime} – ${endTime}` : "";
                // Show the time range only when the bar has room; otherwise
                // the title attribute carries it for hover/tooltip.
                const showRange = range && width >= 90;
                return (
                  <button
                    key={task.id}
                    type="button"
                    className={classes.taskBar}
                    style={{
                      left,
                      width,
                      top,
                      height: taskBarH,
                      backgroundColor: tk.bg,
                      color: tk.ink,
                      borderColor: tk.bar,
                    }}
                    onClick={(e) => {
                      e.stopPropagation();
                      onPick?.({ type: "task", task });
                    }}
                    title={
                      range
                        ? `${tk.label} · ${task.title} · ${range}`
                        : `${tk.label} · ${task.title}`
                    }
                  >
                    <span
                      className={classes.taskBarEdge}
                      style={{ background: tk.bar }}
                    />
                    <span className={classes.taskBarLbl}>
                      {showRange ? `${task.title} · ${range}` : task.title}
                    </span>
                  </button>
                );
              })}

              {/* Single-day task pills (share task lane row 0 with multi-day bars) */}
              {Object.entries(taskBuckets).map(([idxStr, bucket]) => {
                const idx = Number(idxStr);
                const cellLeft = propColW + idx * colW;
                return (
                  <PillBucket
                    key={`${p.id}-${idx}`}
                    bucket={bucket}
                    cellLeft={cellLeft}
                    colW={colW}
                    top={chipTop}
                    onPick={onPick}
                  />
                );
              })}
            </div>
          );
        })}
        {/* Spacer row so the horizontal scrollbar doesn't sit on top of the
            last property's content. Same grid layout as a real row so the
            day-column borders continue down to the bottom of the control. */}
        <div
          className={classes.spacer}
          style={{ gridTemplateColumns: gridCols, width: totalWidth }}
        >
          <div className={classes.prop} />
          {days.map((d) => (
            <div
              key={d.key}
              className={classes.cell}
              data-weekend={d.isWeekend ? "true" : "false"}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

interface PillBucketProps {
  bucket: CalendarSingleDayTask[];
  cellLeft: number;
  colW: number;
  top: number;
  onPick?: (event: CalendarPickedEvent) => void;
}

function PillBucket({ bucket, cellLeft, colW, top, onPick }: PillBucketProps) {
  if (bucket.length === 1) {
    const t = bucket[0];
    const tk = TASK_STYLE[t.kind];
    return (
      <button
        type="button"
        className={classes.taskSingle}
        style={{
          left: cellLeft + 2,
          width: colW - 4,
          top,
          background: tk.bg,
          color: tk.ink,
          borderColor: tk.bar,
        }}
        onClick={(e) => {
          e.stopPropagation();
          onPick?.({ type: "task", task: t });
        }}
        title={tk.label}
      >
        <span className={classes.taskDot} style={{ background: tk.bar }} />
        <span className={classes.taskLbl}>{tk.short}</span>
      </button>
    );
  }
  const seen = new Set<CalendarTaskKind>();
  const distinctKinds = bucket.filter((t) => {
    if (seen.has(t.kind)) return false;
    seen.add(t.kind);
    return true;
  });
  // Multiple tasks share one day. The pill is too small to expose each task,
  // so it opens a popover listing every task as its own clickable row — each
  // picks that specific task (not just the first).
  return (
    <Menu shadow="md" width={240} position="bottom-start" withinPortal>
      <Menu.Target>
        <button
          type="button"
          className={classes.taskPill}
          style={{ left: cellLeft + 2, width: colW - 4, top }}
          onClick={(e) => e.stopPropagation()}
          title={bucket.map((t) => TASK_STYLE[t.kind].label).join("\n")}
        >
          <span className={classes.taskPillNum}>{bucket.length}</span>
          <span className={classes.taskPillDots}>
            {distinctKinds.slice(0, 3).map((t, i) => (
              <span
                key={i}
                className={classes.taskPillDot}
                style={{ background: TASK_STYLE[t.kind].bar }}
              />
            ))}
            {distinctKinds.length > 3 && (
              <span
                className={`${classes.taskPillDot} ${classes.taskPillDotMore}`}
              >
                +
              </span>
            )}
          </span>
        </button>
      </Menu.Target>
      <Menu.Dropdown>
        <Menu.Label>{bucket.length} tasks</Menu.Label>
        {bucket.map((task) => {
          const tk = TASK_STYLE[task.kind];
          return (
            <Menu.Item
              key={task.id}
              onClick={(e) => {
                e.stopPropagation();
                onPick?.({ type: "task", task });
              }}
              leftSection={
                <span
                  style={{
                    display: "inline-block",
                    width: 8,
                    height: 8,
                    borderRadius: 9999,
                    background: tk.bar,
                  }}
                />
              }
            >
              <span
                style={{ fontSize: 13, color: "var(--mantine-color-dark-7)" }}
              >
                {task.title}
              </span>
              <span
                style={{ fontSize: 11, color: "var(--mantine-color-gray-6)" }}
              >
                {" · "}
                {tk.label}
              </span>
            </Menu.Item>
          );
        })}
      </Menu.Dropdown>
    </Menu>
  );
}
