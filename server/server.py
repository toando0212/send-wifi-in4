from flask import Flask, jsonify, render_template, request

app = Flask(__name__)

# Store the latest coordinate
latest_coordinate = {'x': 0, 'y': 0}

@app.route('/')
def index():
    return render_template('index.html')  # Assumes index.html is in the templates folder

@app.route('/coordinates', methods=['GET', 'POST'])
def coordinates():
    global latest_coordinate
    if request.method == 'POST':
        # Update the coordinate with user-provided values
        data = request.json
        if 'x' in data and 'y' in data:
            latest_coordinate = {'x': data['x'], 'y': data['y']}
            return jsonify({"message": "Coordinate updated"}), 200
        return jsonify({"error": "Invalid data"}), 400
    # For GET requests, return the latest coordinate
    return jsonify(latest_coordinate)

@app.route('/upload_csv', methods=['GET','POST'])
def upload_csv():
    # Get the JSON data from the request
    data = request.json
    if not data:
        return jsonify({"error": "No data received"}), 400

    # Log the received data
    # print("Received Data:", data)
    return jsonify({"message": "Data received successfully", "received_data": data}), 200

if __name__ == '_main_':
    app.run(debug=True)