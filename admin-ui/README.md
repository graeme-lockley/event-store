# Event Store Admin UI

A modern, clean admin interface for managing Event Store instances built with
Deno Fresh.

## Features

- **Multi-Store Support**: Connect to multiple Event Store instances
- **Clean Dashboard**: Overview of all stores with health status
- **Topic Management**: View and manage topics across stores
- **Event Browsing**: Browse and search events
- **Consumer Management**: Register and monitor consumers
- **Simple Authentication**: Username/password login
- **Responsive Design**: Works on desktop and mobile

## Quick Start

### Prerequisites

- Deno installed
- Event Store instances running (default: localhost:8000, localhost:8001)

### Installation

1. Navigate to the admin-ui directory:
   ```bash
   cd admin-ui
   ```

2. Start the development server:
   ```bash
   deno task start
   ```

3. Open your browser to `http://localhost:8001`

### Default Credentials

- **Username**: `admin`
- **Password**: `admin123`

## Configuration

### Adding Event Store Instances

Edit the stores configuration in the route handlers (e.g.,
`routes/dashboard.tsx`):

```typescript
const stores: EventStoreConfig[] = [
  { name: "Local Store", url: "http://localhost", port: 8000 },
  { name: "Secondary Store", url: "http://localhost", port: 8001 },
  // Add more stores here
];
```

### Customizing Authentication

Edit `utils/auth.ts` to add more users or change default credentials.

## Development

### Project Structure

```
admin-ui/
├── components/          # Reusable UI components
├── routes/             # Page routes
├── utils/              # Utilities and services
├── static/             # Static assets
└── islands/            # Interactive components
```

### Available Scripts

- `deno task start` - Start development server
- `deno task build` - Build for production
- `deno task preview` - Preview production build
- `deno task check` - Run linting and type checking

## API Integration

The admin UI connects to Event Store instances via their REST API:

- Health checks
- Topic management
- Event retrieval
- Consumer management

## Styling

Built with Tailwind CSS for a clean, modern design with:

- Black and white base theme
- Blue accent colors
- Responsive layout
- Clean typography

## Security

- Simple cookie-based authentication
- HTTP-only cookies
- Session management
- Protected routes

## Browser Support

- Modern browsers with ES2020 support
- Chrome, Firefox, Safari, Edge
