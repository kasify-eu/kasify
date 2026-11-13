type TaskStatus = "pending" | "in_progress" | "completed";
export type TaskType = "cleaning" | "maintenance" | "pool" | "gardening";

type TaskAccess = "owner" | "manager" | "crew";

/**
 * Task details. Most fields are stripped by the backend when a non-manager
 * crew member views a task that isn't assigned to them — only `id`, `from`,
 * `to`, and `property` are guaranteed. Treat `task_type` being absent as
 * the signal for "redacted view".
 */
export type Task = {
  id: string;
  property: import("~/types/properties").Property;
  from: string;
  to: string;
  booking_id?: string | null;
  task_type?: TaskType;
  status?: TaskStatus;
  assigned_to?: string;
  assigned_to_name?: string;
  notes?: string | null;
  owner_notes?: string | null;
  job_report?: string | null;
  public_token?: string | null;
  created_at?: string;
  started_at?: string | null;
  completed_at?: string | null;
  cancelled_at?: string | null;
  next_booking?: import("~/types/bookings").Booking | null;
  access?: TaskAccess;
};

export type TaskChecklistItem = {
  id: string;
  task_id: string;
  description: string;
  photo_required: boolean;
  sort_order: number;
  completed: boolean;
  completed_at: string | null;
};

export type TasksResponse = {
  tasks: Task[];
  page: number | null;
  size: number | null;
  total: number | null;
};

export type CreateTaskRequest = {
  property_id: string;
  booking_id?: string;
  task_type: TaskType;
  from: string;
  to: string;
  assigned_to?: string;
  notes?: string;
  checklist_items?: CreateChecklistItemRequest[];
};

type CreateChecklistItemRequest = {
  description: string;
  photo_required?: boolean;
  sort_order?: number;
};

export type UpdateTaskRequest = {
  assigned_to?: string;
  notes?: string;
  status?: TaskStatus;
  from?: string;
  to?: string;
};

export type TasksFilters = {
  property_id?: string;
  assigned_to?: string;
  status?: TaskStatus;
  task_type?: TaskType;
  scheduled_from?: string;
  scheduled_to?: string;
  include_cancelled?: boolean;
  page?: number;
  size?: number;
};
