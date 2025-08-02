import { Window } from "happy-dom";
import { h } from "preact";
import { render } from "@testing-library/preact";

// Set up DOM environment
const window = new Window();
const document = window.document;
(globalThis as any).document = document;
(globalThis as any).window = window;

// Set up Preact hooks context by creating a container
const container = document.createElement("div");
document.body.appendChild(container);

// Initialize Preact hooks context
function createHooksContext() {
  // Create a simple component that uses hooks to initialize the context
  function TestComponent() {
    return <div>Test</div>;
  }
  
  // Render it to initialize hooks context
  render(<TestComponent />, { container });
}

// Call this to set up hooks context
createHooksContext();

export { render, container }; 