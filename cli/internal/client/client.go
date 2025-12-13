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
	httpClient *http.Client
}

// NewClient creates a new event store API client
func NewClient(baseURL string) *Client {
	return &Client{
		baseURL: baseURL,
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
	Callback string            `json:"callback"`
	Topics   map[string]string `json:"topics"` // topic -> lastEventId (or null)
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
func (c *Client) GetTopics() ([]Topic, error) {
	respBody, err := c.request("GET", "/topics", nil)
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
func (c *Client) GetTopic(name string) (*Topic, error) {
	endpoint := "/topics/" + url.PathEscape(name)
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
func (c *Client) CreateTopic(name string, schemas []Schema) error {
	req := TopicCreationRequest{
		Name:    name,
		Schemas: schemas,
	}

	_, err := c.request("POST", "/topics", req)
	return err
}

// UpdateTopicSchemas updates schemas for an existing topic
func (c *Client) UpdateTopicSchemas(name string, schemas []Schema) error {
	req := TopicUpdateRequest{
		Schemas: schemas,
	}

	endpoint := "/topics/" + url.PathEscape(name)
	_, err := c.request("PUT", endpoint, req)
	return err
}

// GetConsumers lists all registered consumers
func (c *Client) GetConsumers() ([]Consumer, error) {
	respBody, err := c.request("GET", "/consumers", nil)
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
func (c *Client) RegisterConsumer(callback string, topics map[string]string) (string, error) {
	req := ConsumerRegistrationRequest{
		Callback: callback,
		Topics:   topics,
	}

	respBody, err := c.request("POST", "/consumers/register", req)
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
func (c *Client) DeleteConsumer(id string) error {
	endpoint := "/consumers/" + url.PathEscape(id)
	_, err := c.request("DELETE", endpoint, nil)
	return err
}

// GetEvents retrieves events from a topic
func (c *Client) GetEvents(topic string, query *EventsQuery) ([]Event, error) {
	endpoint := "/topics/" + url.PathEscape(topic) + "/events"
	
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
