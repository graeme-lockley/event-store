# 🚀 Event Store Deployment Guide

This guide covers deploying the complete Event Store system with both the
backend and admin UI.

## 📋 System Overview

The Event Store system consists of two main components:

1. **Event Store Backend** (Port 8000) - File-backed event storage with REST API
2. **Admin UI** (Port 8001) - Modern web interface for management

## 🛠 Prerequisites

- **Deno** (v2.0.0+) installed
- Network access for both ports

## 🚀 Quick Deployment

### 1. Start the Event Store Backend

```bash
# From the event-store directory
cd event-store
deno task start
```

The Event Store will start on port 8000 with the following features:

- File-backed storage in `data/` directory
- REST API for all operations
- JSON Schema validation
- Asynchronous event dispatching

### 2. Start the Admin UI

```bash
# From the admin-ui directory
cd admin-ui
deno task start
```

The Admin UI will start on port 8001 with:

- Modern Fresh-based interface
- Multi-store support
- Authentication system
- Real-time monitoring

### 3. Access the System

- **Admin UI**: http://localhost:8001
  - Login: `admin` / `admin`
- **Event Store API**: http://localhost:8000

## 🔧 Configuration

### Event Store Configuration

The Event Store uses environment variables:

```bash
export PORT=8000  # Default port
export DATA_DIR="./data"  # Event storage directory
export CONFIG_DIR="./config"  # Topic configuration directory
```

### Admin UI Configuration

The Admin UI automatically manages store configurations in `data/stores.json`.
The default configuration includes:

```json
[
  { "name": "Local Store", "url": "http://localhost", "port": 8000 },
  { "name": "Secondary Store", "url": "http://localhost", "port": 8001 }
]
```

You can modify store configurations through the Admin UI interface or by editing
the `data/stores.json` file directly.

### Authentication

Default credentials are managed in `data/users.json`:

```json
[
  {
    "username": "admin",
    "passwordHash": "hashed_password",
    "createdAt": "2024-01-01T00:00:00.000Z"
  }
]
```

The default login is `admin` / `admin`. Users are stored with SHA-256 hashed
passwords for security.

## 📊 Testing the Deployment

Run the test suite to verify everything is working:

```bash
# Test the Event Store backend
cd event-store
deno task test

# Test the Admin UI
cd ../admin-ui
deno task test
```

This will test:

- Event Store API functionality
- Admin UI components and routes
- Authentication flow
- Store configuration management

## 🔍 Monitoring

### Event Store Health

```bash
curl http://localhost:8000/health
```

Response:

```json
{
  "status": "healthy",
  "consumers": 0,
  "runningDispatchers": []
}
```

### Admin UI Status

```bash
curl -I http://localhost:8001
```

Should return a 302 redirect to `/login`.

## 📁 File Structure

```
event-store/
├── event-store/             # Event Store backend
│   ├── mod.ts              # Entry point
│   ├── api/                # API routes
│   ├── core/               # Core modules
│   ├── utils/              # Utilities
│   ├── config/             # Topic configurations
│   ├── data/               # Event storage
│   └── deno.json           # Deno configuration
├── admin-ui/               # Admin interface
│   ├── routes/             # UI pages
│   ├── components/         # UI components
│   ├── islands/            # Interactive components
│   ├── utils/              # UI utilities
│   ├── static/             # Static assets
│   ├── tests/              # Test suite
│   └── deno.json           # Deno configuration
├── docs/                   # Documentation
└── README.md               # Project overview
```

## 🔐 Security Considerations

### Event Store

- No built-in authentication (intended for internal networks)
- File system permissions control access
- Schema validation prevents invalid events

### Admin UI

- Simple username/password authentication
- HTTP-only cookies
- Protected routes with middleware
- Change default credentials in production

## 🚀 Production Deployment

### Event Store Backend

1. **Environment Setup**:
   ```bash
   export PORT=8000
   export DATA_DIR="/var/lib/event-store/data"
   export CONFIG_DIR="/var/lib/event-store/config"
   ```

2. **File Permissions**:
   ```bash
   sudo mkdir -p /var/lib/event-store/{data,config}
   sudo chown -R deno:deno /var/lib/event-store
   ```

3. **Service File** (systemd):
   ```ini
   [Unit]
   Description=Event Store
   After=network.target

   [Service]
   Type=simple
   User=deno
   WorkingDirectory=/path/to/event-store/event-store
   ExecStart=/usr/local/bin/deno run --allow-net --allow-read --allow-write --allow-env mod.ts
   Restart=always

   [Install]
   WantedBy=multi-user.target
   ```

### Admin UI

1. **Build for Production**:
   ```bash
   cd admin-ui
   deno task build
   ```

2. **Deploy to Deno Deploy**:
   ```bash
   deno deploy --project=event-store-admin admin-ui/main.ts
   ```

3. **Environment Variables**:
   ```bash
   export DATA_DIR="/var/lib/admin-ui/data"
   export DEBUG="false"
   ```

## 🔄 Backup and Recovery

### Event Store Data

The Event Store data is stored in the `data/` directory:

```bash
# Backup
tar -czf event-store-backup-$(date +%Y%m%d).tar.gz event-store/data/ event-store/config/

# Restore
tar -xzf event-store-backup-20250115.tar.gz
```

### Admin UI Data

The Admin UI stores configuration in `data/` directory:

```bash
# Backup
tar -czf admin-ui-backup-$(date +%Y%m%d).tar.gz admin-ui/data/

# Restore
tar -xzf admin-ui-backup-20250115.tar.gz
```

## 📈 Scaling

### Horizontal Scaling

1. **Multiple Event Store Instances**:
   - Run multiple Event Store instances on different ports
   - Configure Admin UI to connect to all instances via the store management
     interface
   - Use load balancer for API requests

2. **Admin UI Scaling**:
   - Deploy Admin UI to multiple regions
   - Use shared data directory for user and store configurations
   - Implement proper authentication with external providers

### Vertical Scaling

1. **Event Store**:
   - Increase file system performance (SSD)
   - Optimize directory structure
   - Monitor memory usage

2. **Admin UI**:
   - Increase Deno memory limits
   - Optimize bundle size
   - Use CDN for static assets

## 🐛 Troubleshooting

### Common Issues

1. **Port Already in Use**:
   ```bash
   lsof -i :8000  # Check what's using port 8000
   lsof -i :8001  # Check what's using port 8001
   ```

2. **Permission Denied**:
   ```bash
   chmod +x mod.ts
   chmod -R 755 data/ config/
   ```

3. **Admin UI Not Loading**:
   ```bash
   cd admin-ui
   deno task start  # Check for errors
   ```

4. **Event Store Not Responding**:
   ```bash
   curl http://localhost:8000/health  # Check health
   tail -f event-store/data/  # Check for file system issues
   ```

### Logs

- **Event Store**: Console output
- **Admin UI**: Console output and browser developer tools

## 📞 Support

For issues and questions:

1. Check the logs for error messages
2. Verify network connectivity
3. Test individual components
4. Review configuration files

## 📝 License

This project is open source and available under the MIT License.
