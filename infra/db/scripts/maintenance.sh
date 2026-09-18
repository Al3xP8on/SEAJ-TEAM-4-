#!/bin/bash

# Clean and reset database

echo "Stopping containers..."
docker-compose down -v

echo "Clearing logs..."
rm -f logs/*.log 2>/dev/null

echo "Spinning up fresh database..."
docker-compose up -d

echo "Waiting for services to be ready..."
sleep 5

echo "✓ Database reset complete!"
