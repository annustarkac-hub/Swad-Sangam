import { Eye, EyeOff } from "lucide-react";
import { useState, type InputHTMLAttributes } from "react";

/** Password box with a show/hide button (48 px target). Used by the admin login and the customer account page. */
export default function PasswordInput({ showLabel = "Show password", hideLabel = "Hide password", ...props }:
  InputHTMLAttributes<HTMLInputElement> & { showLabel?: string; hideLabel?: string }) {
  const [show, setShow] = useState(false);
  return (
    <span className="pw-wrap">
      <input {...props} type={show ? "text" : "password"} />
      <button type="button" className="pw-toggle" aria-label={show ? hideLabel : showLabel} aria-pressed={show} onClick={() => setShow(!show)}>
        {show ? <EyeOff size={20} aria-hidden /> : <Eye size={20} aria-hidden />}
      </button>
    </span>
  );
}
