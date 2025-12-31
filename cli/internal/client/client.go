package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"time"
)

// Client represents an HTTP client for the event store API
type Client struct {
	baseURL    string
	apiKey     string
	httpClient *http.Client
}

// NewClient creates a new event store API client
func NewClient(baseURL, apiKey string) *Client {
	return &Client{
		baseURL: baseURL,
		apiKey:  apiKey,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// ErrorResponse represents an API error response
type ErrorResponse struct {
	Error string `json:"error"`
	Code  string `json:"code,omitempty"`
}

// Topic represents a topic in the event store
type Topic struct {
	Name     string   `json:"name"`
	Sequence int      `json:"sequence"`
	Schemas  []Schema `json:"schemas"`
}

// Schema represents a JSON schema for an event type
type Schema struct {
	EventType  string                 `json:"eventType"`
	Type       string                 `json:"type"`
	Schema     string                 `json:"$schema"`
	Properties map[string]interface{} `json:"properties"`
	Required   []string               `json:"required"`
}

// TopicsResponse represents the response from GET /topics
type TopicsResponse struct {
	Topics []Topic `json:"topics"`
}

// TopicCreationRequest represents a request to create a topic
type TopicCreationRequest struct {
	Name    string   `json:"name"`
	Schemas []Schema `json:"schemas"`
}

// TopicUpdateRequest represents a request to update a topic
type TopicUpdateRequest struct {
	Schemas []Schema `json:"schemas"`
}

// MessageResponse represents a simple message response
type MessageResponse struct {
	Message string `json:"message"`
}

// Consumer represents a consumer in the event store
type Consumer struct {
	ID       string            `json:"id"`
	Callback string            `json:"callback"`
	Topics   map[string]string `json:"topics"` // topic -> lastEventId (or null)
}

// ConsumersResponse represents the response from GET /consumers
type ConsumersResponse struct {
	Consumers []Consumer `json:"consumers"`
}

// ConsumerRegistrationRequest represents a request to register a consumer
type ConsumerRegistrationRequest struct {
	Callback string             `json:"callback"`
	Topics   map[string]*string `json:"topics"` // topic -> lastEventId (nil for null, pointer to string for value)
}

// ConsumerRegistrationResponse represents the response from POST /consumers/register
type ConsumerRegistrationResponse struct {
	ConsumerID string `json:"consumerId"`
}

// Event represents an event in the event store
type Event struct {
	ID        string                 `json:"id"`
	Timestamp string                 `json:"timestamp"`
	Type      string                 `json:"type"`
	Payload   map[string]interface{} `json:"payload"`
}

// Health represents the health status of the event store
type Health struct {
	Status             string   `json:"status"`
	Consumers          int      `json:"consumers"`
	RunningDispatchers []string `json:"runningDispatchers"`
}

// EventsResponse represents the response from GET /topics/{topic}/events
type EventsResponse struct {
	Events []Event `json:"events"`
}

// EventsQuery represents query parameters for getting events
type EventsQuery struct {
	SinceEventID string
	Date         string
	Limit        int
}

// Quota represents quota settings for a tenant
type Quota struct {
	MaxTopics        int   `json:"maxTopics"`
	MaxNamespaces    int   `json:"maxNamespaces"`
	MaxEventsPerDay  int64 `json:"maxEventsPerDay"`
	MaxConsumers     int   `json:"maxConsumers"`
	MaxUsers         int   `json:"maxUsers"`
	MaxEventSizeBytes int64 `json:"maxEventSizeBytes"`
}

// Tenant represents a tenant in the event store
type Tenant struct {
	ID        string                 `json:"id"`
	Name      string                 `json:"name"`
	CreatedAt string                 `json:"createdAt"`
	UpdatedAt string                 `json:"updatedAt,omitempty"`
	DeletedAt string                 `json:"deletedAt,omitempty"`
	Quota     *Quota                 `json:"quota,omitempty"`
	Metadata  map[string]interface{} `json:"metadata"`
}

// TenantCreateRequest represents a request to create a tenant
type TenantCreateRequest struct {
	ID       string                 `json:"id"`
	Name     string                 `json:"name"`
	Quota    *Quota                 `json:"quota,omitempty"`
	Metadata map[string]interface{} `json:"metadata,omitempty"`
}

// TenantUpdateRequest represents a request to update a tenant
type TenantUpdateRequest struct {
	Name     string                 `json:"name,omitempty"`
	Quota    *Quota                 `json:"quota,omitempty"`
	Metadata map[string]interface{} `json:"metadata,omitempty"`
}

// TenantListResponse represents the response from GET /tenants
type TenantListResponse struct {
	Tenants []Tenant `json:"tenants"`
}

// Namespace represents a namespace in the event store
type Namespace struct {
	TenantID    string                 `json:"tenantId"`
	ID          string                 `json:"id"`
	Name        string                 `json:"name"`
	Description string                 `json:"description,omitempty"`
	CreatedAt   string                 `json:"createdAt"`
	UpdatedAt   string                 `json:"updatedAt,omitempty"`
	DeletedAt   string                 `json:"deletedAt,omitempty"`
	Metadata    map[string]interface{} `json:"metadata"`
}

// NamespaceCreateRequest represents a request to create a namespace
type NamespaceCreateRequest struct {
	ID          string                 `json:"id"`
	Name        string                 `json:"name"`
	Description string                 `json:"description,omitempty"`
	Metadata    map[string]interface{} `json:"metadata,omitempty"`
}

// NamespaceUpdateRequest represents a request to update a namespace
type NamespaceUpdateRequest struct {
	Name        string                 `json:"name,omitempty"`
	Description string                 `json:"description,omitempty"`
	Metadata    map[string]interface{} `json:"metadata,omitempty"`
}

// NamespaceListResponse represents the response from GET /tenants/{tenantId}/namespaces
type NamespaceListResponse struct {
	Namespaces []Namespace `json:"namespaces"`
}

// User represents a user in the event store
type User struct {
	ID             string                 `json:"id"`
	Email          string                 `json:"email"`
	Name           string                 `json:"name"`
	Status         string                 `json:"status"`
	CreatedAt      string                 `json:"createdAt"`
	UpdatedAt      string                 `json:"updatedAt,omitempty"`
	LastLoginAt    string                 `json:"lastLoginAt,omitempty"`
	EmailVerified  bool                   `json:"emailVerified"`
	PrimaryTenantID string                `json:"primaryTenantId,omitempty"`
	Metadata       map[string]interface{} `json:"metadata"`
}

// UserCreateRequest represents a request to create a user
type UserCreateRequest struct {
	Email          string                 `json:"email"`
	Name           string                 `json:"name"`
	Password       string                 `json:"password"`
	Metadata       map[string]interface{} `json:"metadata,omitempty"`
	PrimaryTenantID string                `json:"primaryTenantId,omitempty"`
}

// UserUpdateRequest represents a request to update a user
type UserUpdateRequest struct {
	Email    string                 `json:"email,omitempty"`
	Name     string                 `json:"name,omitempty"`
	Metadata map[string]interface{} `json:"metadata,omitempty"`
}

// UserListResponse represents the response from GET /tenants/{tenantId}/users
type UserListResponse struct {
	Users []User `json:"users"`
}

// AssignUserTenantRequest represents a request to assign a user to a tenant
type AssignUserTenantRequest struct {
	TenantID  string `json:"tenantId"`
	Role      string `json:"role,omitempty"`
	IsPrimary bool   `json:"isPrimary,omitempty"`
}

// APIKey represents an API key in the event store
type APIKey struct {
	ID          string   `json:"id"`
	UserID      string   `json:"userId"`
	Name        string   `json:"name"`
	Description string   `json:"description,omitempty"`
	CreatedAt   string   `json:"createdAt"`
	ExpiresAt   string   `json:"expiresAt,omitempty"`
	LastUsedAt  string   `json:"lastUsedAt,omitempty"`
	RevokedAt   string   `json:"revokedAt,omitempty"`
	Scopes      []string `json:"scopes,omitempty"`
	IsActive    bool     `json:"isActive"`
	Key         string   `json:"key,omitempty"` // Only present on creation
}

// CreateAPIKeyRequest represents a request to create an API key
type CreateAPIKeyRequest struct {
	Name        string   `json:"name"`
	Description string   `json:"description,omitempty"`
	ExpiresAt   string   `json:"expiresAt,omitempty"` // ISO 8601 format
	Scopes      []string `json:"scopes,omitempty"`
}

// APIKeyListResponse represents the response from GET /tenants/{tenantId}/users/{userId}/api-keys
type APIKeyListResponse struct {
	APIKeys []APIKey `json:"apiKeys"`
}

// request performs an HTTP request and returns the response body
func (c *Client) request(method, endpoint string, body interface{}) ([]byte, error) {
	var reqBody io.Reader
	if body != nil {
		jsonData, err := json.Marshal(body)
		if (err) != nil {
			return nil, fmt.Errorf("failed to marshal request body: %w", err)
		}
		reqBody = bytes.NewBuffer(jsonData)
	}

	req, err := http.NewRequest(method, c.baseURL+endpoint, reqBody)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	if c.apiKey != "" {
		req.Header.Set("Authorization", "Bearer "+c.apiKey)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		var errResp ErrorResponse
		if err := json.Unmarshal(respBody, &errResp); err == nil && errResp.Error != "" {
			return nil, fmt.Errorf("API error: %s (code: %s)", errResp.Error, errResp.Code)
		}
		return nil, fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(respBody))
	}

	return respBody, nil
}

// GetTopics lists all topics
func (c *Client) GetTopics(tenantID, namespaceID string) ([]Topic, error) {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/namespaces/" + url.PathEscape(namespaceID) + "/topics"
	respBody, err := c.request("GET", endpoint, nil)
	if err != nil {
		return nil, err
	}

	var resp TopicsResponse
	if err := json.Unmarshal(respBody, &resp); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return resp.Topics, nil
}

// GetTopic gets detailed information about a specific topic
func (c *Client) GetTopic(tenantID, namespaceID, name string) (*Topic, error) {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/namespaces/" + url.PathEscape(namespaceID) + "/topics/" + url.PathEscape(name)
	respBody, err := c.request("GET", endpoint, nil)
	if err != nil {
		return nil, err
	}

	var topic Topic
	if err := json.Unmarshal(respBody, &topic); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &topic, nil
}

// CreateTopic creates a new topic with schemas
func (c *Client) CreateTopic(tenantID, namespaceID, name string, schemas []Schema) error {
	req := TopicCreationRequest{
		Name:    name,
		Schemas: schemas,
	}

	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/namespaces/" + url.PathEscape(namespaceID) + "/topics"
	_, err := c.request("POST", endpoint, req)
	return err
}

// UpdateTopicSchemas updates schemas for an existing topic
func (c *Client) UpdateTopicSchemas(tenantID, namespaceID, name string, schemas []Schema) error {
	req := TopicUpdateRequest{
		Schemas: schemas,
	}

	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/namespaces/" + url.PathEscape(namespaceID) + "/topics/" + url.PathEscape(name)
	_, err := c.request("PUT", endpoint, req)
	return err
}

// GetConsumers lists all registered consumers
func (c *Client) GetConsumers(tenantID, namespaceID string) ([]Consumer, error) {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/namespaces/" + url.PathEscape(namespaceID) + "/consumers"
	respBody, err := c.request("GET", endpoint, nil)
	if err != nil {
		return nil, err
	}

	var resp ConsumersResponse
	if err := json.Unmarshal(respBody, &resp); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return resp.Consumers, nil
}

// RegisterConsumer registers a new consumer
// topics map: empty string or "null" means null (start from beginning), otherwise the event ID
func (c *Client) RegisterConsumer(tenantID, namespaceID, callback string, topics map[string]string) (string, error) {
	// Convert map[string]string to map[string]*string for proper null handling
	topicsWithNull := make(map[string]*string)
	for topic, eventID := range topics {
		if eventID == "" || eventID == "null" {
			// Set to nil to send JSON null
			topicsWithNull[topic] = nil
		} else {
			// Set to pointer to string value
			eventIDCopy := eventID
			topicsWithNull[topic] = &eventIDCopy
		}
	}

	req := ConsumerRegistrationRequest{
		Callback: callback,
		Topics:   topicsWithNull,
	}

	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/namespaces/" + url.PathEscape(namespaceID) + "/consumers/register"
	respBody, err := c.request("POST", endpoint, req)
	if err != nil {
		return "", err
	}

	var resp ConsumerRegistrationResponse
	if err := json.Unmarshal(respBody, &resp); err != nil {
		return "", fmt.Errorf("failed to parse response: %w", err)
	}

	return resp.ConsumerID, nil
}

// DeleteConsumer unregisters a consumer
func (c *Client) DeleteConsumer(tenantID, namespaceID, id string) error {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/namespaces/" + url.PathEscape(namespaceID) + "/consumers/" + url.PathEscape(id)
	_, err := c.request("DELETE", endpoint, nil)
	return err
}

// GetEvents retrieves events from a topic
func (c *Client) GetEvents(tenantID, namespaceID, topic string, query *EventsQuery) ([]Event, error) {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/namespaces/" + url.PathEscape(namespaceID) + "/topics/" + url.PathEscape(topic) + "/events"

	// Build query parameters
	params := url.Values{}
	if query != nil {
		if query.SinceEventID != "" {
			params.Add("sinceEventId", query.SinceEventID)
		}
		if query.Date != "" {
			params.Add("date", query.Date)
		}
		if query.Limit > 0 {
			params.Add("limit", fmt.Sprintf("%d", query.Limit))
		}
	}

	if len(params) > 0 {
		endpoint += "?" + params.Encode()
	}

	respBody, err := c.request("GET", endpoint, nil)
	if err != nil {
		return nil, err
	}

	var resp EventsResponse
	if err := json.Unmarshal(respBody, &resp); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return resp.Events, nil
}

// GetHealth retrieves the health status of the event store
func (c *Client) GetHealth() (*Health, error) {
	respBody, err := c.request("GET", "/health", nil)
	if err != nil {
		return nil, err
	}

	var health Health
	if err := json.Unmarshal(respBody, &health); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &health, nil
}

// EventPublishRequest represents a request to publish an event
type EventPublishRequest struct {
	Topic   string                 `json:"topic"`
	Type    string                 `json:"type"`
	Payload map[string]interface{} `json:"payload"`
}

// EventPublishResponse represents the response from POST /events
type EventPublishResponse struct {
	EventIDs []string `json:"eventIds"`
}

// PublishEvents publishes one or more events
func (c *Client) PublishEvents(tenantID, namespaceID string, events []EventPublishRequest) ([]string, error) {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/namespaces/" + url.PathEscape(namespaceID) + "/events"
	respBody, err := c.request("POST", endpoint, events)
	if err != nil {
		return nil, err
	}

	var resp EventPublishResponse
	if err := json.Unmarshal(respBody, &resp); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return resp.EventIDs, nil
}

// CreateTenant creates a new tenant
func (c *Client) CreateTenant(req TenantCreateRequest) (*Tenant, error) {
	respBody, err := c.request("POST", "/tenants", req)
	if err != nil {
		return nil, err
	}

	var tenant Tenant
	if err := json.Unmarshal(respBody, &tenant); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &tenant, nil
}

// ListTenants lists all tenants
func (c *Client) ListTenants() ([]Tenant, error) {
	respBody, err := c.request("GET", "/tenants", nil)
	if err != nil {
		return nil, err
	}

	var resp TenantListResponse
	if err := json.Unmarshal(respBody, &resp); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return resp.Tenants, nil
}

// GetTenant gets tenant details
func (c *Client) GetTenant(tenantID string) (*Tenant, error) {
	endpoint := "/tenants/" + url.PathEscape(tenantID)
	respBody, err := c.request("GET", endpoint, nil)
	if err != nil {
		return nil, err
	}

	var tenant Tenant
	if err := json.Unmarshal(respBody, &tenant); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &tenant, nil
}

// UpdateTenant updates a tenant
func (c *Client) UpdateTenant(tenantID string, req TenantUpdateRequest) (*Tenant, error) {
	endpoint := "/tenants/" + url.PathEscape(tenantID)
	respBody, err := c.request("PUT", endpoint, req)
	if err != nil {
		return nil, err
	}

	var tenant Tenant
	if err := json.Unmarshal(respBody, &tenant); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &tenant, nil
}

// DeleteTenant deletes a tenant (soft delete)
func (c *Client) DeleteTenant(tenantID string, reason string) error {
	endpoint := "/tenants/" + url.PathEscape(tenantID)
	var body interface{}
	if reason != "" {
		body = map[string]string{"reason": reason}
	}
	_, err := c.request("DELETE", endpoint, body)
	return err
}

// CreateNamespace creates a new namespace
func (c *Client) CreateNamespace(tenantID string, req NamespaceCreateRequest) (*Namespace, error) {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/namespaces"
	respBody, err := c.request("POST", endpoint, req)
	if err != nil {
		return nil, err
	}

	var namespace Namespace
	if err := json.Unmarshal(respBody, &namespace); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &namespace, nil
}

// ListNamespaces lists namespaces in a tenant
func (c *Client) ListNamespaces(tenantID string) ([]Namespace, error) {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/namespaces"
	respBody, err := c.request("GET", endpoint, nil)
	if err != nil {
		return nil, err
	}

	var resp NamespaceListResponse
	if err := json.Unmarshal(respBody, &resp); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return resp.Namespaces, nil
}

// GetNamespace gets namespace details
func (c *Client) GetNamespace(tenantID, namespaceID string) (*Namespace, error) {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/namespaces/" + url.PathEscape(namespaceID)
	respBody, err := c.request("GET", endpoint, nil)
	if err != nil {
		return nil, err
	}

	var namespace Namespace
	if err := json.Unmarshal(respBody, &namespace); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &namespace, nil
}

// UpdateNamespace updates a namespace
func (c *Client) UpdateNamespace(tenantID, namespaceID string, req NamespaceUpdateRequest) (*Namespace, error) {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/namespaces/" + url.PathEscape(namespaceID)
	respBody, err := c.request("PUT", endpoint, req)
	if err != nil {
		return nil, err
	}

	var namespace Namespace
	if err := json.Unmarshal(respBody, &namespace); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &namespace, nil
}

// DeleteNamespace deletes a namespace
func (c *Client) DeleteNamespace(tenantID, namespaceID string, reason string) error {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/namespaces/" + url.PathEscape(namespaceID)
	var body interface{}
	if reason != "" {
		body = map[string]string{"reason": reason}
	}
	_, err := c.request("DELETE", endpoint, body)
	return err
}

// CreateUser creates a new user
func (c *Client) CreateUser(tenantID string, req UserCreateRequest) (*User, error) {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/users"
	respBody, err := c.request("POST", endpoint, req)
	if err != nil {
		return nil, err
	}

	var user User
	if err := json.Unmarshal(respBody, &user); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &user, nil
}

// ListUsers lists users in a tenant
func (c *Client) ListUsers(tenantID string) ([]User, error) {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/users"
	respBody, err := c.request("GET", endpoint, nil)
	if err != nil {
		return nil, err
	}

	var resp UserListResponse
	if err := json.Unmarshal(respBody, &resp); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return resp.Users, nil
}

// GetUser gets user details
func (c *Client) GetUser(tenantID, userID string) (*User, error) {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/users/" + url.PathEscape(userID)
	respBody, err := c.request("GET", endpoint, nil)
	if err != nil {
		return nil, err
	}

	var user User
	if err := json.Unmarshal(respBody, &user); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &user, nil
}

// UpdateUser updates a user
func (c *Client) UpdateUser(tenantID, userID string, req UserUpdateRequest) (*User, error) {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/users/" + url.PathEscape(userID)
	respBody, err := c.request("PUT", endpoint, req)
	if err != nil {
		return nil, err
	}

	var user User
	if err := json.Unmarshal(respBody, &user); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &user, nil
}

// DeleteUser removes user from tenant
func (c *Client) DeleteUser(tenantID, userID string) error {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/users/" + url.PathEscape(userID)
	_, err := c.request("DELETE", endpoint, nil)
	return err
}

// AssignUserToTenant assigns a user to a tenant
func (c *Client) AssignUserToTenant(tenantID, userID string, req AssignUserTenantRequest) error {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/users/" + url.PathEscape(userID) + "/tenants"
	_, err := c.request("POST", endpoint, req)
	return err
}

// CreateAPIKey creates a new API key for a user
func (c *Client) CreateAPIKey(tenantID, userID string, req CreateAPIKeyRequest) (*APIKey, error) {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/users/" + url.PathEscape(userID) + "/api-keys"
	respBody, err := c.request("POST", endpoint, req)
	if err != nil {
		return nil, err
	}

	var apiKey APIKey
	if err := json.Unmarshal(respBody, &apiKey); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &apiKey, nil
}

// ListAPIKeys lists API keys for a user
func (c *Client) ListAPIKeys(tenantID, userID string) ([]APIKey, error) {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/users/" + url.PathEscape(userID) + "/api-keys"
	respBody, err := c.request("GET", endpoint, nil)
	if err != nil {
		return nil, err
	}

	var resp APIKeyListResponse
	if err := json.Unmarshal(respBody, &resp); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return resp.APIKeys, nil
}

// GetAPIKey gets API key details
func (c *Client) GetAPIKey(tenantID, userID, keyID string) (*APIKey, error) {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/users/" + url.PathEscape(userID) + "/api-keys/" + url.PathEscape(keyID)
	respBody, err := c.request("GET", endpoint, nil)
	if err != nil {
		return nil, err
	}

	var apiKey APIKey
	if err := json.Unmarshal(respBody, &apiKey); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &apiKey, nil
}

// RevokeAPIKey revokes an API key
func (c *Client) RevokeAPIKey(tenantID, userID, keyID string) error {
	endpoint := "/tenants/" + url.PathEscape(tenantID) + "/users/" + url.PathEscape(userID) + "/api-keys/" + url.PathEscape(keyID)
	_, err := c.request("DELETE", endpoint, nil)
	return err
}
