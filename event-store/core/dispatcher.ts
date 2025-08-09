import { ConsumerManager } from "./consumers.ts";
import { EventManager } from "./events.ts";

export class Dispatcher {
  private intervals = new Map<string, number>();
  private consumerManager: ConsumerManager;
  private eventManager: EventManager;
  private checkInterval = 500; // 500ms as specified in PRD

  constructor(consumerManager: ConsumerManager, eventManager: EventManager) {
    this.consumerManager = consumerManager;
    this.eventManager = eventManager;
  }

  /**
   * Start dispatcher for a topic
   */
  startDispatcher(topic: string): void {
    if (this.intervals.has(topic)) {
      return; // Already running
    }

    const intervalId = setInterval(async () => {
      await this.checkAndDeliverEvents(topic);
    }, this.checkInterval);

    this.intervals.set(topic, intervalId);
    console.log(`Started dispatcher for topic: ${topic}`);
  }

  /**
   * Stop dispatcher for a topic
   */
  stopDispatcher(topic: string): void {
    const intervalId = this.intervals.get(topic);
    if (intervalId) {
      clearInterval(intervalId);
      this.intervals.delete(topic);
      console.log(`Stopped dispatcher for topic: ${topic}`);
    }
  }

  /**
   * Stop all dispatchers
   */
  stopAllDispatchers(): void {
    for (const [topic, intervalId] of this.intervals) {
      clearInterval(intervalId);
      console.log(`Stopped dispatcher for topic: ${topic}`);
    }
    this.intervals.clear();
  }

  /**
   * Check for new events and deliver to consumers
   */
  private async checkAndDeliverEvents(topic: string): Promise<void> {
    try {
      // Always trigger delivery handling; consumers will fetch only what they need.
      // This avoids double-reading events per interval.
      await this.consumerManager.nudgeConsumersForTopic(topic);
    } catch (error) {
      console.error(`Error in dispatcher for topic ${topic}:`, error);
    }
  }

  /**
   * Manually trigger delivery for a topic (useful for immediate delivery)
   */
  async triggerDelivery(topic: string): Promise<void> {
    await this.consumerManager.nudgeConsumersForTopic(topic);
  }

  /**
   * Get all running dispatchers
   */
  getRunningDispatchers(): string[] {
    return Array.from(this.intervals.keys());
  }

  /**
   * Check if dispatcher is running for a topic
   */
  isDispatcherRunning(topic: string): boolean {
    return this.intervals.has(topic);
  }

  /**
   * Set the check interval (useful for testing)
   */
  setCheckInterval(interval: number): void {
    this.checkInterval = interval;
  }
}
