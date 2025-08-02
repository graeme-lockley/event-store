# Event Store

A lightweight, file-backed, API-driven message recording and delivery system with a modern web-based admin interface.

## 🏗️ Architecture

This repository contains two main components:

### 1. Event Store Core (`/event-store`)
- **File-backed storage** with per-event files
- **JSON Schema validation** for events
- **Asynchronous event dispatching** to consumers
- **RESTful API** for topic management, event publishing, and consumer registration

### 2. Admin UI (`/admin-ui`)
- **Modern web interface** built with Fresh (Deno)
- **Real-time health monitoring** of event stores
- **User management** with persistent storage
- **Topic and event browsing**
- **Consumer monitoring**

## 🚀 Quick Start

### Prerequisites
- [Deno](https://deno.land/) 1.40+

### Running the Event Store
```bash
cd event-store
deno task start
```

### Running the Admin UI
```bash
cd admin-ui
deno task start
```

## 📚 Documentation

- [Event Store Core Documentation](event-store/README.md)
- [Admin UI Documentation](admin-ui/README.md)
- [API Reference](docs/API.md)
- [Deployment Guide](docs/DEPLOYMENT.md)

## 🧪 Testing

```bash
# Test Event Store Core
cd event-store
deno test

# Test Admin UI
cd admin-ui
deno test
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## 📄 License

MIT License - see LICENSE file for details
