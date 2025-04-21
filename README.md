# Java Concurrency: Set Game Project

This repository contains my implementation of a multithreaded version of the "Set" card game, developed as part of a university assignment in Java. The project focuses on Java concurrency, synchronization, and thread-safe design.

## 🎯 Project Goal

To implement the core logic of the Set card game using Java threads, where multiple players (human or simulated) compete in real-time to find valid sets of cards based on shared features.

## 🛠 Technologies Used

- Java
- Java Threads and Synchronization
- Maven (Build tool)
- JUnit (for testing)
- Swing-based User Interface (provided by course)

## 🚀 How to Run

1. Make sure you have **Java** and **Maven** installed on your machine.

2. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/java-concurrency-set-game.git
   cd java-concurrency-set-game
   ```

3. Build and run:
   ```bash
   mvn clean compile exec:java
   ```

## ✅ Features

- Full multithreaded implementation of the Set game.
- Fair queue-based dealer logic (FIFO).
- Support for both human and AI players.
- Timed reshuffling of cards if no set is found.
- Synchronization where needed for thread safety.

## 🏅 Bonus Features (If Implemented)

- No use of magic numbers (uses configuration fields).
- Graceful thread termination in reverse creation order.
- Configurable countdown timer behavior.
- Efficient thread sleep/wake logic to save resources.

## ✍️ Author

- [Niv Yaakobov](https://github.com/Niv-Yaakobov)

> Created as part of a course assignment under the guidance of Yair, Doron Cohen, and Gur Elkin.
