import type { ReactNode } from "react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { Menu } from "@mantine/core";
import classes from "./calendar-month.module.css";
import { CHANNEL_STYLE, TASK_STYLE } from "~/components/calendar-shared/styles";
import type {
  CalendarBooking,
  CalendarFilterKey,
  CalendarMultiDayTask,
  CalendarPickedEvent,
  CalendarRow,
  CalendarSingleDayTask,
  CalendarTask,
  CalendarTaskKind,
} from "~/components/calendar-timeline/types";

interface CalendarMonthProps {
  /** Single property's bookings + tasks. */
  row: CalendarRow | null;
  /** First day of the month being displayed. */
  monthStart: Date;
  /** Reference "today" — used for the today indicator. Defaults to new Date(). */
  today?: Date;
  /** Filter which event types are visible. Defaults to all. */
  activeTypes?: Set<CalendarFilterKey>;
  onPick?: (event: CalendarPickedEvent) => void;
  /** Clicking an empty day cell requests creating a task on that day. */
  onCreate?: (propertyId: string, date: Date) => void;
}

const DEFAULT_ACTIVE: Set<CalendarFilterKey> = new Set([
  "booking",
  "cleaning",
  "maintenance",
  "pool",
  "gardening",
  "unknown",
]);

const RES_BAR_H = 28;
const TASK_BAR_H = 18;
const LANE_GAP = 3;
const TASK_LANE_GAP = 8;
const PAD_TOP = 32;
const PAD_BOTTOM = 8;
const MIN_CELL_H = 99;

function parseDate(s: string): Date {
  const dateOnly = s.length > 10 ? s.slice(0, 10) : s;
  const [y, m, d] = dateOnly.split("-").map(Number);
  return new Date(y, m - 1, d);
}

// Fraction of the day (0..1) implied by the time-of-day portion of an ISO
// datetime string. Date-only strings (length < 16) collapse to 0.
function timeFraction(iso: string): number {
  if (iso.length < 16) return 0;
  const hh = parseInt(iso.slice(11, 13), 10);
  const mm = parseInt(iso.slice(14, 16), 10);
  if (Number.isNaN(hh) || Number.isNaN(mm)) return 0;
  return (hh * 60 + mm) / (24 * 60);
}

// "HH:mm" pulled directly from the ISO string. Empty when no time is present.
function timeLabel(iso: string): string {
  return iso.length < 16 ? "" : iso.slice(11, 16);
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
function diffDays(aStr: string, bStr: string): number {
  return Math.round(
    (parseDate(bStr).getTime() - parseDate(aStr).getTime()) / 86400000,
  );
}

interface DayCell {
  date: Date;
  key: string;
  inMonth: boolean;
  isWeekend: boolean;
}

/**
 * Build the visible grid: from the Monday of the week containing day 1
 * through the Sunday of the week containing the last day. Always whole weeks.
 */
function buildMonthDays(monthStart: Date): DayCell[] {
  const year = monthStart.getFullYear();
  const month = monthStart.getMonth();
  const first = new Date(year, month, 1);
  const last = new Date(year, month + 1, 0);
  const startOffset = (first.getDay() + 6) % 7; // Monday-first
  const start = addDays(first, -startOffset);
  const totalCells = Math.ceil((startOffset + last.getDate()) / 7) * 7;
  const out: DayCell[] = [];
  for (let i = 0; i < totalCells; i++) {
    const d = addDays(start, i);
    out.push({
      date: d,
      key: dayKey(d),
      inMonth: d.getMonth() === month,
      isWeekend: isWeekend(d),
    });
  }
  return out;
}

interface WeekSegment {
  wIdx: number;
  col: number;
  span: number;
  isStart: boolean;
  isEnd: boolean;
  /** Fractional offset within the first day cell (0..1). Non-zero only on the start segment. */
  startFrac: number;
  /** Fractional offset within the last day cell (0..1). Non-zero only on the end segment. */
  endFrac: number;
}

/**
 * Split a span [startStr, endStr) into per-week segments aligned to the
 * Monday-Sunday week rows of `days`. Continuation segments carry isStart/isEnd
 * flags so the bars can render flush at week breaks.
 *
 * Both date-only (`YYYY-MM-DD`, end exclusive) and full ISO datetime
 * (`YYYY-MM-DDTHH:mm:ss…`, end inclusive) inputs are accepted. With a time
 * portion present, the end day is partially included and `endFrac` carries the
 * within-cell ratio so the rendering can clip the bar to the actual end time.
 */
function splitToWeekSegments(
  startStr: string,
  endStr: string,
  days: DayCell[],
  keyToIdx: Map<string, number>,
): WeekSegment[] {
  const start = parseDate(startStr);
  const startFrac = timeFraction(startStr);
  const endFrac = timeFraction(endStr);
  const endDay = parseDate(endStr);
  // When endStr carries a time, the end day is partially covered → make it
  // inclusive in the loop by extending the exclusive end by one day.
  const end = endFrac > 0 ? addDays(endDay, 1) : endDay;
  const segs: WeekSegment[] = [];
  let cur = startOfDay(start);
  const endTime = end.getTime();
  const startMs = startOfDay(start).getTime();
  while (cur.getTime() < endTime) {
    const idx = keyToIdx.get(dayKey(cur));
    if (idx === undefined) {
      // before grid — jump forward by a day
      cur = addDays(cur, 1);
      continue;
    }
    const wIdx = Math.floor(idx / 7);
    const col = idx % 7;
    const weekStart = days[wIdx * 7].date;
    const weekEnd = addDays(weekStart, 7);
    const segEnd = end < weekEnd ? end : weekEnd;
    const span = Math.round((segEnd.getTime() - cur.getTime()) / 86400000);
    const isStart = cur.getTime() === startMs;
    const isEnd = segEnd.getTime() === end.getTime();
    segs.push({
      wIdx,
      col,
      span,
      isStart,
      isEnd,
      startFrac: isStart ? startFrac : 0,
      endFrac: isEnd ? endFrac : 0,
    });
    cur = segEnd;
  }
  return segs;
}

interface PlacedSegment<T> {
  item: T;
  segment: WeekSegment;
  lane: number;
}

/**
 * Greedy per-week lane packing. Each week's lanes are independent: if week 1
 * has 3 overlapping bookings and week 2 has 1, week 2 only stacks 1 lane high.
 */
function layoutBarsPerWeek<T>(
  items: T[],
  getBounds: (item: T) => { startStr: string; endStr: string },
  days: DayCell[],
  keyToIdx: Map<string, number>,
): { placements: PlacedSegment<T>[]; laneCounts: Record<number, number> } {
  const sorted = items.slice().sort((a, b) => {
    const A = getBounds(a);
    const B = getBounds(b);
    return A.startStr.localeCompare(B.startStr);
  });
  const weekLaneEnds: Record<number, number[]> = {};
  const placements: PlacedSegment<T>[] = [];
  for (const it of sorted) {
    const { startStr, endStr } = getBounds(it);
    const segs = splitToWeekSegments(startStr, endStr, days, keyToIdx);
    for (const seg of segs) {
      const lanes = (weekLaneEnds[seg.wIdx] = weekLaneEnds[seg.wIdx] ?? []);
      let lane = lanes.findIndex((endCol) => endCol <= seg.col);
      if (lane === -1) {
        lane = lanes.length;
        lanes.push(seg.col + seg.span);
      } else {
        lanes[lane] = seg.col + seg.span;
      }
      placements.push({ item: it, segment: seg, lane });
    }
  }
  const laneCounts: Record<number, number> = {};
  for (const [w, ls] of Object.entries(weekLaneEnds)) {
    laneCounts[Number(w)] = ls.length;
  }
  return { placements, laneCounts };
}

function isSpanTask(t: CalendarTask): t is CalendarMultiDayTask {
  return t.span === true;
}

export function CalendarMonth({
  row,
  monthStart,
  today = new Date(),
  activeTypes = DEFAULT_ACTIVE,
  onPick,
  onCreate,
}: CalendarMonthProps) {
  const { t, i18n } = useTranslation();

  const days = useMemo(() => buildMonthDays(monthStart), [monthStart]);
  const keyToIdx = useMemo(() => {
    const map = new Map<string, number>();
    days.forEach((d, i) => map.set(d.key, i));
    return map;
  }, [days]);
  const todayKey = useMemo(() => dayKey(startOfDay(today)), [today]);

  const weeks = useMemo(() => {
    const out: DayCell[][] = [];
    for (let i = 0; i < days.length; i += 7) out.push(days.slice(i, i + 7));
    return out;
  }, [days]);

  // Localized weekday header (Monday-first, abbreviated).
  const weekdayLabels = useMemo(() => {
    const fmt = new Intl.DateTimeFormat(i18n.language, { weekday: "short" });
    // Pick any known Monday — Jan 5, 2026 is Monday — and walk 7 days.
    const ref = new Date(2026, 0, 5);
    return Array.from({ length: 7 }, (_, i) => {
      const label = fmt.format(addDays(ref, i));
      return label.charAt(0).toLocaleUpperCase(i18n.language) + label.slice(1);
    });
  }, [i18n.language]);

  const monthShortFmt = useMemo(
    () => new Intl.DateTimeFormat(i18n.language, { month: "short" }),
    [i18n.language],
  );

  const propBookings = row?.bookings ?? [];
  const propTasks = useMemo(
    () => (row?.tasks ?? []).filter((t) => activeTypes.has(t.kind)),
    [row, activeTypes],
  );

  const showBookings = activeTypes.has("booking");

  const resPlacements = useMemo(
    () =>
      showBookings
        ? layoutBarsPerWeek(
            propBookings,
            (b) => ({ startStr: b.in, endStr: b.out }),
            days,
            keyToIdx,
          )
        : { placements: [], laneCounts: {} as Record<number, number> },
    [showBookings, propBookings, days, keyToIdx],
  );

  const spanTasks = useMemo(
    () => propTasks.filter(isSpanTask),
    [propTasks],
  );
  const taskBarPlacements = useMemo(
    () =>
      layoutBarsPerWeek(
        spanTasks,
        (t) => ({ startStr: t.start, endStr: t.end }),
        days,
        keyToIdx,
      ),
    [spanTasks, days, keyToIdx],
  );

  const taskBuckets = useMemo(() => {
    const out: Record<string, CalendarSingleDayTask[]> = {};
    for (const tk of propTasks) {
      if (isSpanTask(tk)) continue;
      const idx = keyToIdx.get(tk.date);
      if (idx === undefined) continue;
      (out[tk.date] = out[tk.date] ?? []).push(tk);
    }
    for (const arr of Object.values(out)) {
      arr.sort((a, b) => a.id.localeCompare(b.id));
    }
    return out;
  }, [propTasks, keyToIdx]);

  function weekHasSingleDayTask(week: DayCell[]): boolean {
    return week.some((d) => taskBuckets[d.key]?.length);
  }

  function weekRowHeight(wIdx: number): number {
    const resLanes = resPlacements.laneCounts[wIdx] ?? 0;
    const taskBarLanes = taskBarPlacements.laneCounts[wIdx] ?? 0;
    const hasSingle = weekHasSingleDayTask(weeks[wIdx]);
    const taskLanes = taskBarLanes + (hasSingle ? 1 : 0);
    const resBlock =
      resLanes > 0 ? resLanes * RES_BAR_H + (resLanes - 1) * LANE_GAP : 0;
    const taskBlock =
      taskLanes > 0 ? taskLanes * TASK_BAR_H + (taskLanes - 1) * LANE_GAP : 0;
    const sep = resBlock > 0 && taskBlock > 0 ? TASK_LANE_GAP : 0;
    const inner = resBlock + sep + taskBlock;
    return Math.max(MIN_CELL_H, PAD_TOP + inner + PAD_BOTTOM);
  }

  if (!row) {
    return (
      <div className={classes.wrap}>
        <div className={classes.empty}>{t("calendar.noProperties")}</div>
      </div>
    );
  }

  return (
    <div className={classes.wrap}>
      <div className={classes.head}>
        {weekdayLabels.map((label, i) => (
          <div key={i} className={classes.wd}>
            {label}
          </div>
        ))}
      </div>

      <div className={classes.grid}>
        {weeks.map((week, wIdx) => {
          const rowH = weekRowHeight(wIdx);
          const resLanes = resPlacements.laneCounts[wIdx] ?? 0;
          const taskBarLanes = taskBarPlacements.laneCounts[wIdx] ?? 0;
          const hasSingle = weekHasSingleDayTask(week);
          const resBlockH =
            resLanes > 0 ? resLanes * RES_BAR_H + (resLanes - 1) * LANE_GAP : 0;
          const sep =
            resBlockH > 0 && (taskBarLanes > 0 || hasSingle)
              ? TASK_LANE_GAP
              : 0;
          const taskBlockTop = PAD_TOP + resBlockH + sep;
          const singleDayTop =
            taskBlockTop + taskBarLanes * (TASK_BAR_H + LANE_GAP);

          return (
            <div
              key={wIdx}
              className={classes.week}
              style={{ height: rowH }}
            >
              {week.map((d) => {
                const isToday = d.key === todayKey;
                const isFirstOfMonth = d.date.getDate() === 1;
                const handleCreate =
                  onCreate && row
                    ? () => onCreate(row.id, d.date)
                    : undefined;
                return (
                  <div
                    key={d.key}
                    className={
                      handleCreate
                        ? `${classes.cell} ${classes.cellClickable}`
                        : classes.cell
                    }
                    data-out={d.inMonth ? "false" : "true"}
                    data-weekend={d.isWeekend ? "true" : "false"}
                    data-today={isToday ? "true" : "false"}
                    role={handleCreate ? "button" : undefined}
                    tabIndex={handleCreate ? 0 : undefined}
                    aria-label={handleCreate ? t("calendar.addTask") : undefined}
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
                    <div className={classes.num}>
                      <span className={isToday ? classes.today : ""}>
                        {d.date.getDate()}
                      </span>
                      {isFirstOfMonth && (
                        <span className={classes.monthLbl}>
                          {monthShortFmt.format(d.date)}
                        </span>
                      )}
                    </div>
                    {handleCreate && (
                      <span className={classes.cellAdd} aria-hidden="true">
                        +
                      </span>
                    )}
                  </div>
                );
              })}

              {/* Reservation bars */}
              {(() => {
                const bars: ReactNode[] = [];
                let i = 0;
                for (const p of resPlacements.placements) {
                  if (p.segment.wIdx !== wIdx) continue;
                  bars.push(
                    <ReservationBar
                      key={`r-${p.item.id}-${i}`}
                      booking={p.item}
                      segment={p.segment}
                      lane={p.lane}
                      onPick={onPick}
                    />,
                  );
                  i++;
                }
                return bars;
              })()}

              {/* Multi-day task bars */}
              {(() => {
                const bars: ReactNode[] = [];
                let i = 0;
                for (const p of taskBarPlacements.placements) {
                  if (p.segment.wIdx !== wIdx) continue;
                  bars.push(
                    <TaskSpanBar
                      key={`tb-${p.item.id}-${i}`}
                      task={p.item}
                      segment={p.segment}
                      lane={p.lane}
                      taskBlockTop={taskBlockTop}
                      onPick={onPick}
                    />,
                  );
                  i++;
                }
                return bars;
              })()}

              {/* Single-day task chips (one shared lane below all multi-day bars) */}
              {week.map((d) => {
                const bucket = taskBuckets[d.key];
                if (!bucket || !bucket.length) return null;
                const col = (keyToIdx.get(d.key) ?? 0) % 7;
                return (
                  <SingleDayBucket
                    key={`sd-${d.key}`}
                    bucket={bucket}
                    col={col}
                    top={singleDayTop}
                    onPick={onPick}
                  />
                );
              })}
            </div>
          );
        })}
      </div>
    </div>
  );
}

interface ReservationBarProps {
  booking: CalendarBooking;
  segment: WeekSegment;
  lane: number;
  onPick?: (event: CalendarPickedEvent) => void;
}

function ReservationBar({ booking, segment, lane, onPick }: ReservationBarProps) {
  const ch = CHANNEL_STYLE[booking.channel];
  // Continuous left/right edges in column units. startFrac shifts check-in
  // partway into its day cell; endFrac clips check-out partway through its
  // day cell. Date-only inputs collapse to full-cell rendering (both 0).
  const leftCol = segment.col + segment.startFrac;
  const rightCol =
    segment.isEnd && segment.endFrac > 0
      ? segment.col + segment.span - 1 + segment.endFrac
      : segment.col + segment.span;
  const widthCols = Math.max(0, rightCol - leftCol);
  const left = `calc(${(leftCol / 7) * 100}% + 4px)`;
  const width = `calc(${(widthCols / 7) * 100}% - 8px)`;
  const top = PAD_TOP + lane * (RES_BAR_H + LANE_GAP);
  const nights = Math.max(1, diffDays(booking.in, booking.out));
  const checkin = timeLabel(booking.in);
  const checkout = timeLabel(booking.out);
  const titleAttr =
    checkin && checkout
      ? `${booking.guest} · ${ch.name} · ${checkin} → ${checkout}`
      : `${booking.guest} · ${ch.name}`;
  return (
    <button
      type="button"
      className={classes.bar}
      data-start={segment.isStart ? "true" : "false"}
      data-end={segment.isEnd ? "true" : "false"}
      style={{
        left,
        width,
        top,
        height: RES_BAR_H,
        background: ch.bg,
        borderColor: ch.bar,
        color: ch.ink,
      }}
      onClick={() => onPick?.({ type: "booking", booking })}
      title={titleAttr}
    >
      {segment.isStart && (
        <span className={classes.barEdge} style={{ background: ch.bar }} />
      )}
      {segment.isStart ? (
        <>
          {booking.channel !== "unknown" && (
            <span className={classes.barChannel} style={{ background: ch.bar }}>
              {ch.name[0]}
            </span>
          )}
          <span className={classes.barGuest}>{booking.guest}</span>
          <span className={classes.barMeta}>· {nights}n</span>
        </>
      ) : (
        <span className={classes.barCont}>{booking.guest} (cont.)</span>
      )}
    </button>
  );
}

interface TaskSpanBarProps {
  task: CalendarMultiDayTask;
  segment: WeekSegment;
  lane: number;
  taskBlockTop: number;
  onPick?: (event: CalendarPickedEvent) => void;
}

function TaskSpanBar({
  task,
  segment,
  lane,
  taskBlockTop,
  onPick,
}: TaskSpanBarProps) {
  const tk = TASK_STYLE[task.kind];
  // Continuous left/right edges in column units. startFrac shifts the start
  // partway into the first day cell; endFrac clips the end partway through
  // the last day cell. Both are 0 for date-only spans, so the math collapses
  // to the previous full-cell behavior.
  const leftCol = segment.col + segment.startFrac;
  const rightCol =
    segment.isEnd && segment.endFrac > 0
      ? segment.col + segment.span - 1 + segment.endFrac
      : segment.col + segment.span;
  const widthCols = Math.max(0, rightCol - leftCol);
  const left = `calc(${(leftCol / 7) * 100}% + 4px)`;
  const width = `calc(${(widthCols / 7) * 100}% - 8px)`;
  const top = taskBlockTop + lane * (TASK_BAR_H + LANE_GAP);
  const startTime = timeLabel(task.start);
  const endTime = timeLabel(task.end);
  const range = startTime && endTime ? `${startTime} – ${endTime}` : "";
  const titleAttr = range
    ? `${tk.label} · ${task.title} · ${range}`
    : `${tk.label} · ${task.title}`;
  return (
    <button
      type="button"
      className={classes.taskBar}
      data-start={segment.isStart ? "true" : "false"}
      data-end={segment.isEnd ? "true" : "false"}
      style={{
        left,
        width,
        top,
        height: TASK_BAR_H,
        backgroundColor: tk.bg,
        color: tk.ink,
        borderColor: tk.bar,
      }}
      onClick={(e) => {
        e.stopPropagation();
        onPick?.({ type: "task", task });
      }}
      title={titleAttr}
    >
      {segment.isStart && (
        <span className={classes.taskBarEdge} style={{ background: tk.bar }} />
      )}
      <span className={classes.taskBarLbl}>
        {segment.isStart
          ? range
            ? `${task.title} · ${range}`
            : task.title
          : `${task.title} (cont.)`}
      </span>
    </button>
  );
}

interface SingleDayBucketProps {
  bucket: CalendarSingleDayTask[];
  col: number;
  top: number;
  onPick?: (event: CalendarPickedEvent) => void;
}

function SingleDayBucket({ bucket, col, top, onPick }: SingleDayBucketProps) {
  const left = `calc(${(col / 7) * 100}% + 4px)`;
  const width = `calc(${(1 / 7) * 100}% - 8px)`;
  if (bucket.length === 1) {
    const tk = TASK_STYLE[bucket[0].kind];
    const task = bucket[0];
    return (
      <button
        type="button"
        className={classes.taskChip}
        style={{
          left,
          width,
          top,
          height: TASK_BAR_H,
          background: tk.bg,
          color: tk.ink,
          borderColor: tk.bar,
        }}
        onClick={(e) => {
          e.stopPropagation();
          onPick?.({ type: "task", task });
        }}
        title={`${tk.label} · ${task.title}`}
      >
        <span className={classes.taskChipEdge} style={{ background: tk.bar }} />
        <span className={classes.taskChipDot} style={{ background: tk.bar }} />
        <span className={classes.taskChipLbl}>{task.title}</span>
      </button>
    );
  }
  const seen = new Set<CalendarTaskKind>();
  const distinctKinds = bucket.filter((tk) => {
    if (seen.has(tk.kind)) return false;
    seen.add(tk.kind);
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
          style={{ left, width, top, height: TASK_BAR_H }}
          onClick={(e) => e.stopPropagation()}
          title={bucket
            .map((tk) => `${TASK_STYLE[tk.kind].label} · ${tk.title}`)
            .join("\n")}
        >
          <span className={classes.taskPillNum}>{bucket.length} tasks</span>
          <span className={classes.taskPillDots}>
            {distinctKinds.slice(0, 4).map((tk, i) => (
              <span
                key={i}
                className={classes.taskPillDot}
                style={{ background: TASK_STYLE[tk.kind].bar }}
              />
            ))}
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
              <span style={{ fontSize: 13, color: "var(--mantine-color-dark-7)" }}>
                {task.title}
              </span>
              <span style={{ fontSize: 11, color: "var(--mantine-color-gray-6)" }}>
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
