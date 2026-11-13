import toast from "react-hot-toast";

const defaultToastStyle = {
  borderRadius: "10px",
  background: "#333",
  color: "#fff",
  padding: "16px",
  marginRight: "8px",
};

export function showSuccessToast(message: string, icon = "👏") {
  toast(message, {
    icon,
    style: defaultToastStyle,
  });
}

export function showErrorToast(message: string, icon = "❌") {
  toast(message, {
    icon,
    style: {
      ...defaultToastStyle,
      background: "#dc2626",
    },
  });
}

export function showInfoToast(message: string, icon = "ℹ️") {
  toast(message, {
    icon,
    style: {
      ...defaultToastStyle,
      background: "#2563eb",
    },
  });
}

export function showWarningToast(message: string, icon = "⚠️") {
  toast(message, {
    icon,
    style: {
      ...defaultToastStyle,
      background: "#f59e0b",
    },
  });
}
