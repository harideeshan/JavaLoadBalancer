# 1. Use an official Java Runtime as a base image
FROM bellsoft/liberica-openjdk-debian:21

# 2. Set the directory inside the container where our code will live
WORKDIR /app

# 3. Copy your Java files from your Mac into the container
COPY . /app

# 4. Compile the Java files inside the container
RUN javac SimpleLoadBalancer.java BackendServer.java

# 5. The command to run when the container starts
# We'll override this in docker-compose for the workers
CMD ["java", "SimpleLoadBalancer"]