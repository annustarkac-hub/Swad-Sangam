import { Component, type ReactNode } from "react";

/** Keeps the admin shell (menu, top bar) working when one page hits an error, and offers a retry. */
export default class ErrorBoundary extends Component<{ resetKey: string; children: ReactNode }, { error: Error | null }> {
  state: { error: Error | null } = { error: null };
  static getDerivedStateFromError(error: Error) { return { error }; }
  componentDidUpdate(prev: { resetKey: string }) {
    if (prev.resetKey !== this.props.resetKey && this.state.error) this.setState({ error: null });
  }
  componentDidCatch(error: Error) { console.error("Page error", error); }
  render() {
    if (!this.state.error) return this.props.children;
    return (
      <div className="card" role="alert" style={{ maxWidth: 560 }}>
        <h2 style={{ marginTop: 0 }}>This page could not be shown</h2>
        <p className="muted">Something unexpected came back from the server. Your data is safe. Try again, or open another page from the menu.</p>
        <p className="small muted">{this.state.error.message}</p>
        <div className="row">
          <button onClick={() => this.setState({ error: null })}>Try again</button>
          <button className="secondary" onClick={() => location.reload()}>Reload app</button>
        </div>
      </div>
    );
  }
}
