# REST Controller Template

## Requirements
1. Follow RESTful conventions
2. Use proper HTTP status codes
3. Include @Valid for request validation
4. Return ResponseEntity with appropriate types
5. Add OpenAPI documentation annotations
6. Include proper error handling

## Standard Endpoints
- GET /api/resource - List all (paginated)
- GET /api/resource/{id} - Get single resource
- POST /api/resource - Create new resource
- PUT /api/resource/{id} - Update resource
- DELETE /api/resource/{id} - Delete resource

## Security
- All endpoints require authentication except health checks
- Use @PreAuthorize for role-based access
